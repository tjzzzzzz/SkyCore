package dev.skylite.ui.general

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.module.general.SlotBinding
import dev.skylite.ui.render.Fonts
import dev.skylite.ui.render.Ui
import dev.skylite.ui.theme.Theme
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class SlotBindingScreen(private val parent: Screen?) : Screen(Component.literal("Slot Binding")) {

    private companion object {
        val TITLE = Fonts.label("Slot Binding", Fonts.SEMIBOLD)
        val CLEAR = Fonts.label("Clear All", Fonts.SMALL)
        val BACK = Fonts.label("Back", Fonts.SMALL)
        const val PAD = 18
        const val BTN_H = 18
    }

    private var clearHover = 0f
    private var backHover = 0f
    private var lastFrameNanos = 0L
    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() = layout()

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        layout()
    }

    private fun layout() {
        panelW = (width - 100).coerceIn(400, 560)
        panelH = (height - 80).coerceIn(280, 420)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    override fun onClose() {
        SlotBinding.captureKeybind()
        SkyLiteConfig.save()
        val back = parent
        if (back != null) minecraft.setScreenAndShow(back) else super.onClose()
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        g.fill(0, 0, width, height, 0x99000000.toInt())
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = now

        Ui.shadow(g, panelX, panelY, panelW, panelH, 6)
        Ui.panel(g, panelX, panelY, panelW, panelH, Theme.SURFACE, Theme.BORDER, 6)
        g.text(font, TITLE, panelX + PAD, panelY + 16, Theme.TEXT, false)

        val keyName = if (SlotBinding.keyReady()) {
            KeyMappingHelper.getBoundKeyOf(SlotBinding.slotBindingKey).displayName.string
        } else {
            "Unbound"
        }
        g.text(font, Fonts.label("Keybind: $keyName", Fonts.SMALL), panelX + PAD, panelY + 38, Theme.ACCENT, false)

        var y = panelY + 62
        y = helpLine(g, "Shift + Left Click", "Swap with the bound hotbar slot.", y)
        y = helpLine(g, "Press & move key", "Hover a slot, hold the key, release on another.", y)
        y = helpLine(g, "Press & release", "Clear binds for the hovered slot.", y)
        y += 8

        g.text(font, Fonts.label("Current Binds", Fonts.MEDIUM), panelX + PAD, y, Theme.TEXT, false)
        y += 18

        val binds = SlotBinding.bindsSnapshot()
        if (binds.isEmpty() || binds.values.all { it.isEmpty() }) {
            g.text(font, Fonts.label("No binds configured.", Fonts.SMALL), panelX + PAD, y, Theme.TEXT_MUTED, false)
        } else {
            for (hb in 1..8) {
                val list = binds["hotbar$hb"] ?: continue
                if (list.isEmpty()) continue
                val line = Fonts.label("Hotbar $hb  →  ${list.joinToString(", ")}", Fonts.SMALL)
                g.text(font, line, panelX + PAD, y, Theme.TEXT_MUTED, false)
                y += 14
                if (y > panelY + panelH - 50) break
            }
        }

        val clearW = Fonts.width(CLEAR) + 24
        val backW = Fonts.width(BACK) + 24
        clearHover = button(g, CLEAR, panelX + panelW - PAD - clearW, panelY + panelH - 30, clearW, clearHover, mouseX, mouseY, dt, true)
        backHover = button(g, BACK, panelX + PAD, panelY + panelH - 30, backW, backHover, mouseX, mouseY, dt, false)
    }

    private fun helpLine(g: GuiGraphicsExtractor, title: String, body: String, y: Int): Int {
        g.text(font, Fonts.label(title, Fonts.SMALL), panelX + PAD, y, Theme.TEXT, false)
        g.text(font, Fonts.label(body, Fonts.SMALL), panelX + PAD + 118, y, Theme.TEXT_MUTED, false)
        return y + 16
    }

    private fun button(
        g: GuiGraphicsExtractor,
        label: Component,
        x: Int,
        y: Int,
        w: Int,
        hover: Float,
        mouseX: Int,
        mouseY: Int,
        dt: Float,
        accent: Boolean
    ): Float {
        val next = Ui.approach(hover, if (Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, BTN_H)) 1f else 0f, dt)
        Ui.panel(
            g, x, y, w, BTN_H,
            Ui.lerpColor(Theme.CONTROL_OFF, Theme.HOVER, next),
            Ui.lerpColor(Theme.BORDER_SOFT, if (accent) Theme.ACCENT else Theme.TEXT_FAINT, next),
            4
        )
        g.text(font, label, x + 12, y + 5, Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, next), false)
        return next
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        val clearW = Fonts.width(CLEAR) + 24
        val backW = Fonts.width(BACK) + 24
        if (Ui.inBounds(event.x, event.y, panelX + panelW - PAD - clearW, panelY + panelH - 30, clearW, BTN_H)) {
            SlotBinding.clearAll()
            return true
        }
        if (Ui.inBounds(event.x, event.y, panelX + PAD, panelY + panelH - 30, backW, BTN_H)) {
            onClose()
            return true
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }
}
