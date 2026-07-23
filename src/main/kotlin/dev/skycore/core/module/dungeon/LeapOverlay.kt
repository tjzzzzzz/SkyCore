package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.Titles
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

object LeapOverlay {

    private const val MENU_NAME = "Spirit Leap"
    private const val NAME_COLOR = 0xFFFFFFFF.toInt()
    private const val DEAD_COLOR = 0xFFAAAAAA.toInt()

    private val leapButtons = ArrayList<LeapButton>()
    private var sentLeapMsg = false

    private val opts get() = SkyCoreConfig.instance.leapOverlay

    fun init() {}

    fun isLeapMenu(title: String): Boolean =
        active() && title == MENU_NAME && DungeonUtil.inDungeons()

    fun shouldReplaceGui(title: String): Boolean = isLeapMenu(title)

    fun onScreenOpen(title: String) {
        if (!isLeapMenu(title)) return
        rebuild()
    }

    fun onScreenClose() {
        leapButtons.clear()
        sentLeapMsg = false
    }

    fun onRender(context: GuiGraphicsExtractor, width: Int, height: Int, mouseX: Int, mouseY: Int) {
        if (!active()) return
        if (leapButtons.isEmpty()) rebuild()
        for (button in leapButtons) {
            button.render(context, width, height, mouseX, mouseY)
        }
    }

    fun onClick(mouseX: Int, mouseY: Int, button: Int, menu: AbstractContainerMenu): Boolean {
        if (!active() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        for (leap in leapButtons) {
            if (leap.isHovered(mouseX, mouseY)) {
                leap.click(menu)
                return true
            }
        }
        return false
    }

    fun onKey(keyCode: Int): Boolean {
        if (!active()) return false
        val keys = intArrayOf(opts.firstKey, opts.secondKey, opts.thirdKey, opts.fourthKey)
        for (i in keys.indices) {
            if (keys[i] != keyCode || keys[i] == GLFW.GLFW_KEY_UNKNOWN) continue
            if (leapButtons.size <= i) continue
            val screen = Minecraft.getInstance().gui.screen() ?: return false
            val menu = (screen as? AbstractContainerScreen<*>)?.menu ?: return false
            leapButtons[i].click(menu)
            return true
        }
        return false
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    private fun rebuild() {
        val self = Minecraft.getInstance().player?.gameProfile?.name.orEmpty()
        val teammates = DungeonUtil.aliveTeammates(excludeSelf = true)
        val targets = ArrayList<LeapTarget>()
        for ((name, dungeonClass) in DungeonUtil.classes()) {
            if (name.equals(self, ignoreCase = true)) continue
            val dead = teammates.none { it.name == name }
            targets += LeapTarget(name, dungeonClass, dead)
        }
        targets.sortWith(compareBy({ it.dungeonClass }, { it.name }))
        while (targets.size < 4) {
            targets += LeapTarget.empty()
        }
        leapButtons.clear()
        sentLeapMsg = false
        for (i in 0 until 4) {
            leapButtons += LeapButton(targets[i], i)
        }
    }

    private data class LeapTarget(
        val name: String,
        val dungeonClass: String,
        val dead: Boolean
    ) {
        companion object {
            fun empty() = LeapTarget("", "Empty", false)
        }
    }

    private class LeapButton(target: LeapTarget, index: Int) {
        private val player = target.name
        private val dungeonClass = target.dungeonClass
        private val dead = target.dead
        private val classColor = when (target.dungeonClass) {
            "Healer" -> opts.healerColor
            "Mage" -> opts.mageColor
            "Berserk" -> opts.bersColor
            "Archer" -> opts.archColor
            "Tank" -> opts.tankColor
            "Empty" -> DEAD_COLOR
            else -> NAME_COLOR
        }
        private val background = 0xAB000000.toInt()
        private val backgroundHover = withAlpha(classColor, 0.67f, 0.33f)
        private val border = classColor or 0xFF000000.toInt()
        private val offsetX = if (index == 0 || index == 2) 0.25f else 0.55f
        private val offsetY = if (index <= 1) 0.25f else 0.55f

        var minX = 0
        var minY = 0
        var maxX = 0
        var maxY = 0

        fun isHovered(mouseX: Int, mouseY: Int): Boolean =
            player.isNotEmpty() && mouseX in minX..maxX && mouseY in minY..maxY

        fun click(menu: AbstractContainerMenu) {
            val client = Minecraft.getInstance()
            val playerEntity = client.player ?: return
            val gameMode = client.gameMode ?: return
            for (slot in menu.slots) {
                if (slot.container === playerEntity.inventory) continue
                val stack = slot.item
                if (stack.item != Items.PLAYER_HEAD) continue
                val lore = ItemData.loreLines(stack)
                if (lore.isEmpty()) continue
                if (ItemData.plain(stack.hoverName) != player) continue
                if (lore.first() != "Click to teleport!") continue
                gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, playerEntity)
                menu.setCarried(ItemStack.EMPTY)
                if (opts.send && opts.message.isNotEmpty() && !sentLeapMsg) {
                    Titles.sendChatOrCommand(opts.message.replace("{name}", player))
                    sentLeapMsg = true
                }
                return
            }
            Titles.info("Could not leap to $player, valid teleport button not found.")
        }

        fun render(context: GuiGraphicsExtractor, width: Int, height: Int, mouseX: Int, mouseY: Int) {
            minX = (width * offsetX).toInt()
            minY = (height * offsetY).toInt()
            maxX = (width * (offsetX + 0.2f)).toInt()
            maxY = (height * (offsetY + 0.2f)).toInt()
            val fill = if (isHovered(mouseX, mouseY)) backgroundHover else background
            context.fill(minX, minY, maxX, maxY, fill)
            if (player.isNotEmpty()) {
                drawBorder(context, minX, minY, maxX - minX, maxY - minY, border)
            }
            val client = Minecraft.getInstance()
            val textScale = (opts.scale / client.options.guiScale().get()).toFloat()
            val textX = minX + (maxX - minX) / 2
            val playerY = (minY + (maxY - minY) * 0.25).toInt()
            val classY = (minY + (maxY - minY) * 0.5).toInt()
            val deadY = (minY + (maxY - minY) * 0.75).toInt()
            drawScaled(context, Component.literal(player), textX, playerY, textScale, NAME_COLOR)
            drawScaled(context, Component.literal(dungeonClass), textX, classY, textScale, classColor)
            if (dead) {
                drawScaled(context, Component.literal("DEAD"), textX, deadY, textScale, DEAD_COLOR)
            }
        }

        private fun drawScaled(
            context: GuiGraphicsExtractor,
            text: Component,
            x: Int,
            y: Int,
            scale: Float,
            color: Int
        ) {
            val font = Minecraft.getInstance().font
            val pose = context.pose()
            pose.pushMatrix()
            pose.translate(x - x * scale, y - y * scale)
            pose.scale(scale)
            val w = font.width(text)
            context.text(font, text, x - w / 2, y, color, false)
            pose.popMatrix()
        }

        private fun drawBorder(context: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
            context.fill(x, y, x + w, y + 1, color)
            context.fill(x, y + h - 1, x + w, y + h, color)
            context.fill(x, y, x + 1, y + h, color)
            context.fill(x + w - 1, y, x + w, y + h, color)
        }

        private fun withAlpha(argb: Int, alpha: Float, brightness: Float): Int {
            val a = (alpha * 255).toInt().coerceIn(0, 255)
            val r = (((argb ushr 16) and 0xFF) * brightness).toInt().coerceIn(0, 255)
            val g = (((argb ushr 8) and 0xFF) * brightness).toInt().coerceIn(0, 255)
            val b = ((argb and 0xFF) * brightness).toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
