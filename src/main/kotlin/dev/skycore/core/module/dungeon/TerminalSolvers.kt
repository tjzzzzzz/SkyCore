package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object TerminalSolvers {

    enum class TerminalType {
        Panes,
        InOrder,
        StartsWith,
        Select,
        Colors,
        Melody,
        None
    }

    private val colorsOrder = listOf(
        Items.STAINED_GLASS_PANE.green(),
        Items.STAINED_GLASS_PANE.yellow(),
        Items.STAINED_GLASS_PANE.orange(),
        Items.STAINED_GLASS_PANE.red(),
        Items.STAINED_GLASS_PANE.blue()
    )

    @Volatile
    private var currentSolution: TerminalSolution? = null

    private val opts get() = SkyCoreConfig.instance.terminalSolvers

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            currentSolution = null
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            currentSolution = null
        }
    }

    fun terminalType(title: String): TerminalType = when {
        title.startsWith("Correct all the panes!") -> TerminalType.Panes
        title.startsWith("Click in order!") -> TerminalType.InOrder
        title.startsWith("What starts with:") && title.endsWith("?") -> TerminalType.StartsWith
        title.startsWith("Select all the") && title.endsWith("items!") -> TerminalType.Select
        title.startsWith("Change all to same color!") -> TerminalType.Colors
        title == "Click the button on time!" -> TerminalType.Melody
        else -> TerminalType.None
    }

    fun currentSolution(): TerminalSolution? = currentSolution

    fun onSlotUpdate(
        title: String,
        slotId: Int,
        stack: ItemStack,
        slot: Slot?,
        containerSlots: Int,
        isContainerSlot: Boolean
    ) {
        if (!active() || !isContainerSlot || slot == null || !DungeonUtil.onFloor("7")) return
        val type = terminalType(title)
        if (currentSolution == null && type != TerminalType.None) {
            currentSolution = TerminalSolution(type)
        }
        if (!isTypeEnabled(type)) return
        val solution = currentSolution ?: return
        when (type) {
            TerminalType.Panes -> {
                when (stack.item) {
                    Items.STAINED_GLASS_PANE.red() -> solution.setEnabled(slot)
                    Items.STAINED_GLASS_PANE.lime() -> solution.setDisabled(slot)
                }
            }
            TerminalType.StartsWith -> {
                val character = title[title.indexOf('\'') + 1].lowercaseChar().toString()
                val stackName = ItemData.plain(stack.hoverName).trim().lowercase(Locale.ROOT)
                if (stackName.isNotEmpty()) {
                    solution.resetIfMismatch(stack, slotId)
                    if (stackName.startsWith(character)) solution.setEnabled(slot)
                    else solution.setDisabled(slot)
                }
            }
            TerminalType.Select -> {
                val color = title.replace("Select all the", "").replace("items!", "").trim()
                val colorName = if (color == "SILVER") "light_gray" else color.lowercase(Locale.ROOT).replace(' ', '_')
                val stackName = ItemData.plain(stack.hoverName).trim().lowercase(Locale.ROOT)
                if (stackName.isNotEmpty()) {
                    for (dye in DyeColor.entries) {
                        if (dye.name.lowercase(Locale.ROOT) == colorName) {
                            solution.resetIfMismatch(stack, slotId)
                            if (checkStackColor(stack, dye, colorName)) solution.setEnabled(slot)
                            else solution.setDisabled(slot)
                            break
                        }
                    }
                }
            }
            TerminalType.InOrder -> {
                when (stack.item) {
                    Items.STAINED_GLASS_PANE.red() -> solution.setEnabled(slot, stack.count)
                    Items.STAINED_GLASS_PANE.lime() -> solution.setDisabled(slot)
                }
            }
            TerminalType.Colors -> {
                val index = colorsOrder.indexOf(stack.item)
                if (index != -1) solution.setEnabled(slot, index)
            }
            else -> {}
        }
    }

    fun shouldCancelClick(
        title: String,
        slot: Slot?,
        slotId: Int,
        button: Int,
        containerId: Int,
        isInventory: Boolean
    ): Boolean {
        if (!active() || slot == null || isInventory || !DungeonUtil.onFloor("7")) return false
        val type = terminalType(title)
        if (!isTypeEnabled(type) && type != TerminalType.Melody) return false
        val solution = currentSolution
        if (solution == null || solution.openedAtTick + opts.firstClickTicks >= DungeonEvents.serverTick) {
            return true
        }
        return when (type) {
            TerminalType.Panes, TerminalType.StartsWith, TerminalType.Select -> {
                if (solution.isClicked(slot.index) || solution.isDisabled(slot.index)) {
                    true
                } else {
                    solution.setClicked(slot, button, containerId)
                    false
                }
            }
            TerminalType.InOrder -> {
                val first = solution.getSolution().firstOrNull { (key, _) ->
                    !solution.isClicked(key) && !solution.isDisabled(key)
                }
                if (first == null || first.first != slotId) {
                    true
                } else {
                    solution.setClicked(slot, button, containerId)
                    false
                }
            }
            TerminalType.Colors -> {
                val solutionEntries = solution.getSolution()
                if (solutionEntries.any { it.first == slotId && it.second == 0 }) {
                    true
                } else {
                    solution.setClicked(slot, button, containerId)
                    false
                }
            }
            TerminalType.Melody -> true
            TerminalType.None -> false
        }
    }

    fun onScreenRender(context: GuiGraphicsExtractor, title: String, menu: AbstractContainerMenu) {
        if (!active() || !DungeonUtil.onFloor("7")) return
        val type = terminalType(title)
        val solution = currentSolution ?: return
        if (!isTypeEnabled(type)) return
        val font = Minecraft.getInstance().font
        when (type) {
            TerminalType.Panes, TerminalType.StartsWith, TerminalType.Select -> {
                val color = when (type) {
                    TerminalType.Panes -> opts.panesColor
                    TerminalType.StartsWith -> opts.startsWithColor
                    TerminalType.Select -> opts.selectColor
                    else -> opts.panesColor
                }
                for ((slotIndex, _) in solution.getSolution()) {
                    val slot = menu.getSlot(slotIndex)
                    if (!solution.isClicked(slotIndex) && !solution.isDisabled(slotIndex)) {
                        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color)
                    } else {
                        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, opts.backgroundColor)
                    }
                }
            }
            TerminalType.InOrder -> {
                var drawnSlots = 0
                for ((slotIndex, value) in solution.getSolution()) {
                    val slot = menu.getSlot(slotIndex)
                    if (drawnSlots == 3 || solution.isClicked(slotIndex) || solution.isDisabled(slotIndex)) {
                        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, opts.backgroundColor)
                    } else {
                        val fill = when (drawnSlots) {
                            1 -> opts.inOrderColorSecond
                            2 -> opts.inOrderColorThird
                            else -> opts.inOrderColorFirst
                        }
                        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fill)
                        if (opts.inOrderDrawNumbers) {
                            val text = value.toString()
                            val w = font.width(text)
                            context.text(font, text, slot.x + 8 - w / 2, slot.y + 4, 0xFFFFFFFF.toInt(), false)
                        }
                        drawnSlots++
                    }
                }
            }
            TerminalType.Colors -> {
                for ((slotIndex, target) in solution.getSolution()) {
                    val slot = menu.getSlot(slotIndex)
                    if (target == 0) {
                        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, opts.backgroundColor)
                    } else {
                        val fill = if (target > 0) opts.colorsColorFirst else opts.colorsColorSecond
                        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fill)
                        val text = target.toString()
                        val w = font.width(text)
                        context.text(font, text, slot.x + 8 - w / 2, slot.y + 4, 0xFFFFFFFF.toInt(), false)
                    }
                }
            }
            else -> {}
        }
    }

    fun shouldHideTooltip(title: String): Boolean =
        active() && terminalType(title) != TerminalType.None

    fun onScreenClose() {
        currentSolution = null
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    private fun isTypeEnabled(type: TerminalType): Boolean = when (type) {
        TerminalType.Panes -> opts.panes
        TerminalType.InOrder -> opts.inOrder
        TerminalType.StartsWith -> opts.startsWith
        TerminalType.Select -> opts.select
        TerminalType.Colors -> opts.colors
        TerminalType.None, TerminalType.Melody -> false
    }

    private fun checkStackColor(stack: ItemStack, color: DyeColor, colorName: String): Boolean {
        val item = stack.item
        if (ItemData.plain(stack.hoverName).trim().isEmpty()) return false
        if (item.toString().startsWith("minecraft:$colorName")) return true
        return when (color) {
            DyeColor.BLACK -> item == Items.INK_SAC
            DyeColor.BLUE -> item == Items.LAPIS_LAZULI
            DyeColor.BROWN -> item == Items.COCOA_BEANS
            DyeColor.WHITE -> item == Items.BONE_MEAL
            else -> false
        }
    }

    class TerminalSolution(val type: TerminalType) {
        val solutionMap = ConcurrentHashMap<Int, Int>()
        private val clickedSet = ConcurrentHashMap.newKeySet<Int>()
        private val contents = ConcurrentHashMap<Int, Item>()
        var openedAtTick: Int = DungeonEvents.serverTick
        var containerId: Int = -1

        fun setEnabled(slot: Slot, count: Int = 1) {
            solutionMap[slot.index] = count
        }

        fun setDisabled(slot: Slot) {
            solutionMap[slot.index] = -1
        }

        fun setClicked(slot: Slot, button: Int, containerId: Int) {
            if (this.containerId == containerId) return
            if (type == TerminalType.Colors) {
                val index = solutionMap.getOrDefault(slot.index, -1)
                if (index != -1) {
                    val modifier = if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) -1 else 1
                    val newIndex = index - modifier
                    solutionMap[slot.index] = when {
                        newIndex < 0 -> 4
                        newIndex > 4 -> 0
                        else -> newIndex
                    }
                }
            } else {
                clickedSet.add(slot.index)
            }
            this.containerId = containerId
            if (opts.soundOnClick) {
                val id = Identifier.tryParse(opts.clickSound) ?: return
                Titles.play(
                    SoundEvent.createVariableRangeEvent(id),
                    opts.clickSoundVolume.toFloat(),
                    opts.clickSoundPitch.toFloat()
                )
            }
        }

        fun isDisabled(slotIndex: Int): Boolean =
            solutionMap.getOrDefault(slotIndex, -1) == -1

        fun isClicked(slotIndex: Int): Boolean = clickedSet.contains(slotIndex)

        fun resetIfMismatch(stack: ItemStack, slotId: Int) {
            val previous = contents[slotId]
            if (previous != null && previous != stack.item) {
                solutionMap.clear()
                clickedSet.clear()
                contents.clear()
                openedAtTick = DungeonEvents.serverTick
                containerId = -1
            }
            contents[slotId] = stack.item
        }

        fun getSolution(): List<Pair<Int, Int>> = when (type) {
            TerminalType.InOrder -> solutionMap.entries
                .sortedBy { it.value }
                .map { it.key to it.value }
            TerminalType.Colors -> {
                val mostCommon = solutionMap.values
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: return emptyList()
                solutionMap.entries.map { entry ->
                    val slotIndex = entry.key
                    var target = -(mostCommon - entry.value)
                    if (abs(target) > 2) {
                        val offset = if (abs(target) == 4) 3 else 1
                        target = -target + if (target > 0) offset else -offset
                    }
                    slotIndex to target
                }
            }
            else -> solutionMap.entries.map { it.key to it.value }
        }
    }
}
