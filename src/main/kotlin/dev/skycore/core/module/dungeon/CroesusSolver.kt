package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.ItemData
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap

object CroesusSolver {

    private enum class LootState {
        Unopened,
        Rerolled,
        Opened,
        OpenedKey,
        Unknown
    }

    private val chestValues = IdentityHashMap<Slot, Double>()
    private val lootHighlights = IdentityHashMap<Slot, Int>()
    private val floorLabels = IdentityHashMap<Slot, String>()
    private val profitHighlights = IdentityHashMap<Slot, Int>()

    private val opts get() = SkyCoreConfig.instance.croesusSolver

    fun init() {
        DungeonPricing.init()
    }

    fun onScreenOpen() {
        chestValues.clear()
        lootHighlights.clear()
        floorLabels.clear()
        profitHighlights.clear()
    }

    fun onScreenClose() = onScreenOpen()

    fun onSlotUpdate(title: String, stack: ItemStack, slot: Slot?, isInventory: Boolean) {
        if (!active() || stack.isEmpty || slot == null || isInventory || !DungeonPricing.inLootArea()) return
        when {
            title == "Croesus" -> highlightLoot(stack, slot)
            title.startsWith("Catacombs - Floor") || title.startsWith("Master Catacombs - Floor") ->
                highlightChest(stack, slot)
        }
    }

    fun onSlotUpdate(title: String, slotId: Int, stack: ItemStack, slot: Slot?, isContainerSlot: Boolean) {
        onSlotUpdate(title, stack, slot, isInventory = !isContainerSlot)
    }

    fun onScreenRender(context: GuiGraphicsExtractor, title: String, menu: AbstractContainerMenu) {
        if (!active() || !DungeonPricing.inLootArea()) return
        val font = Minecraft.getInstance().font
        if (title == "Croesus") {
            for ((slot, color) in lootHighlights) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color)
            }
            if (opts.floorLabel) {
                for ((slot, label) in floorLabels) {
                    val w = font.width(label)
                    context.text(font, label, slot.x + 8 - w / 2, slot.y + 4, 0xFFFFFFFF.toInt(), false)
                }
            }
        } else if (title.startsWith("Catacombs - Floor") || title.startsWith("Master Catacombs - Floor")) {
            for ((slot, color) in profitHighlights) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color)
            }
        }
    }

    fun appendTooltip(slot: Slot?, lines: MutableList<Component>) {
        if (!active() || !opts.valueTooltip || slot == null || !DungeonPricing.inLootArea()) return
        val value = chestValues[slot] ?: return
        val valueText = Component.literal(DungeonPricing.formatSeparator(value))
            .withStyle(
                Style.EMPTY.withColor(
                    TextColor.fromRgb(if (value > 0) 0x55FF55 else 0xFF5555)
                )
            )
        lines += Component.literal("Chest Value: ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)))
            .append(valueText)
    }

    fun appendTooltip(title: String, stack: ItemStack, slot: Slot?, lines: MutableList<Component>) {
        appendTooltip(slot, lines)
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    private fun highlightLoot(stack: ItemStack, slot: Slot) {
        val name = ItemData.plain(stack.hoverName)
        if (!name.endsWith("The Catacombs")) return
        val color = when (lootState(stack)) {
            LootState.Unopened -> opts.unopenedColor
            LootState.Rerolled -> opts.rerolledColor
            LootState.Opened -> opts.openedColor
            LootState.OpenedKey -> opts.openedKeyColor
            LootState.Unknown -> return
        }
        lootHighlights[slot] = color
        if (opts.floorLabel) {
            val prefix = if (name.startsWith("Master Mode")) "M" else "F"
            val floorLine = ItemData.loreLines(stack).firstOrNull() ?: return
            val floor = DungeonPricing.parseRoman(floorLine.substringAfterLast(' '))
            floorLabels[slot] = "$prefix$floor"
        }
    }

    private fun highlightChest(stack: ItemStack, slot: Slot) {
        val name = ItemData.plain(stack.hoverName)
        if (name !in DungeonUtil.chestNames()) return
        val lore = DungeonPricing.loreComponents(stack)
        var value = 0.0
        var cost = 0.0
        var costIndex = -1
        var hasDye = false
        for (i in lore.indices) {
            val text = lore[i]
            val line = ItemData.plain(text)
            if (line.isEmpty() || line == "Contents" || line == "Cost") {
                if (line == "Cost") costIndex = i
                if (line.isEmpty() && costIndex != -1) break
                continue
            }
            if (costIndex == -1) {
                val id = DungeonPricing.marketId(text)
                if (id.startsWith("DYE_")) hasDye = true
                val quantity = if (DungeonPricing.hasItemQuantity(line)) {
                    line.substringAfterLast('x').toIntOrNull() ?: 1
                } else {
                    1
                }
                value += DungeonPricing.lootValue(id) * quantity
            } else if (line.endsWith(" Coins")) {
                cost += line.substringBefore(' ').replace(",", "").toIntOrNull() ?: 0
            }
        }
        chestValues[slot] = value - cost
        val chests = chestValues.entries.sortedByDescending { it.value }
        profitHighlights.clear()
        if (chests.isNotEmpty()) {
            val best = chests.first()
            if (best.value > 0) {
                profitHighlights[best.key] = if (hasDye || best.value >= opts.profitHighThreshold) {
                    opts.profitHighColor
                } else {
                    opts.profitColor
                }
            }
        }
        if (chests.size >= 2) {
            val second = chests[1]
            val keyBuy = DungeonPricing.bazaarBuy("DUNGEON_CHEST_KEY")
            if (keyBuy > 0.0 && second.value - keyBuy > 0.0) {
                profitHighlights[second.key] = opts.profitKeyColor
            } else if (second.value > 0.0) {
                profitHighlights[second.key] = opts.profitSecondaryColor
            }
        }
    }

    private fun lootState(stack: ItemStack): LootState {
        val lines = ItemData.loreLines(stack)
        for (string in lines) {
            if (string == "No chests opened yet!") {
                for (text in DungeonPricing.loreComponents(stack)) {
                    val style = DungeonPricing.findStyle(text) { it.endsWith("Kismet Feather") }
                    if (style != null && style.isStrikethrough) return LootState.Rerolled
                }
                return LootState.Unopened
            }
            if (string.startsWith("Opened Chest: ")) return LootState.Opened
            if (string == "No more chests to open!") return LootState.OpenedKey
        }
        return LootState.Unknown
    }
}
