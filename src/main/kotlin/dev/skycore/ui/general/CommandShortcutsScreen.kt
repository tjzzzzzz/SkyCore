package dev.skycore.ui.general

import dev.skycore.config.SkyCoreConfig
import dev.skycore.ui.render.Fonts
import dev.skycore.ui.render.Ui
import dev.skycore.ui.theme.Theme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.roundToInt

class CommandShortcutsScreen(private val parent: Screen?) : Screen(Component.literal("Command Shortcuts")) {

    private companion object {
        val TITLE = Fonts.label("Command Shortcuts", Fonts.SEMIBOLD)
        val HINT = Fonts.label("Create short aliases for longer commands", Fonts.SMALL)
        val ADD = Fonts.label("Add", Fonts.SMALL)
        val DELETE = Fonts.label("Delete", Fonts.SMALL)
        val BACK = Fonts.label("Back", Fonts.SMALL)
        const val PAD = 16
        const val ROW_H = 56
        const val BTN_H = 18
    }

    private val shortcuts get() = SkyCoreConfig.instance.commandShortcuts.shortcuts

    private var scroll = 0f
    private var focusedRow = -1
    private var focusedField = 0
    private var caretBlink = 0f
    private var lastFrameNanos = 0L
    private var addHover = 0f
    private var backHover = 0f
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
        panelW = (width - 80).coerceIn(420, 700)
        panelH = (height - 60).coerceIn(320, 520)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    override fun onClose() {
        SkyCoreConfig.save()
        val back = parent
        if (back != null) minecraft.setScreenAndShow(back) else super.onClose()
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        g.fill(0, 0, width, height, 0x99000000.toInt())
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = now
        caretBlink = (caretBlink + dt) % 1f

        Ui.shadow(g, panelX, panelY, panelW, panelH, 6)
        Ui.panel(g, panelX, panelY, panelW, panelH, Theme.SURFACE, Theme.BORDER, 6)
        g.text(font, TITLE, panelX + PAD, panelY + 14, Theme.TEXT, false)
        g.text(font, HINT, panelX + PAD, panelY + 30, Theme.TEXT_MUTED, false)

        val addW = Fonts.width(ADD) + 24
        addHover = button(g, ADD, panelX + panelW - PAD - addW, panelY + 12, addW, addHover, mouseX, mouseY, dt, true)

        val listY = panelY + 52
        val listH = panelH - 52 - 40
        val contentH = shortcuts.size * (ROW_H + 6)
        scroll = scroll.coerceIn(0f, max(0f, (contentH - listH).toFloat()))
        g.enableScissor(panelX + 1, listY, panelX + panelW - 1, listY + listH)
        var y = listY - scroll.roundToInt()
        for ((index, entry) in shortcuts.withIndex()) {
            drawRow(g, entry, index, panelX + PAD, y, panelW - PAD * 2, mouseX, mouseY)
            y += ROW_H + 6
        }
        g.disableScissor()

        val backW = Fonts.width(BACK) + 24
        backHover = button(g, BACK, panelX + PAD, panelY + panelH - 30, backW, backHover, mouseX, mouseY, dt, false)
    }

    private fun drawRow(
        g: GuiGraphicsExtractor,
        entry: SkyCoreConfig.CommandShortcutEntry,
        index: Int,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, ROW_H)
        Ui.panel(g, x, y, w, ROW_H, if (hovered) Theme.HOVER else Theme.SURFACE_RAISED, Theme.BORDER_SOFT, 4)
        g.text(font, Fonts.label("Shortcut", Fonts.SMALL), x + 10, y + 6, Theme.TEXT_MUTED, false)
        g.text(font, Fonts.label("Command", Fonts.SMALL), x + 10 + (w - 90) / 2, y + 6, Theme.TEXT_MUTED, false)

        val fieldW = (w - 100) / 2
        drawInput(g, entry.shortcut, index, 0, x + 10, y + 20, fieldW)
        drawInput(g, entry.message, index, 1, x + 16 + fieldW, y + 20, fieldW)

        val delW = Fonts.width(DELETE) + 16
        val delX = x + w - 8 - delW
        Ui.panel(g, delX, y + 19, delW, BTN_H, Theme.CONTROL_OFF, Theme.DANGER, 3)
        g.text(font, DELETE, delX + 8, y + 23, Theme.DANGER, false)
    }

    private fun drawInput(g: GuiGraphicsExtractor, value: String, row: Int, field: Int, x: Int, y: Int, w: Int) {
        val focused = focusedRow == row && focusedField == field
        Ui.panel(g, x, y, w, 22, Theme.CONTROL_OFF, if (focused) Theme.ACCENT else Theme.BORDER_SOFT, 4)
        g.text(font, Fonts.label(value, Fonts.REGULAR), x + 6, y + 7, Theme.TEXT, false)
        if (focused && caretBlink < 0.5f) {
            val caretX = x + 6 + Fonts.width(Fonts.label(value, Fonts.REGULAR))
            g.fill(caretX, y + 5, caretX + 1, y + 17, Theme.ACCENT)
        }
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
        Ui.panel(g, x, y, w, BTN_H, Ui.lerpColor(Theme.CONTROL_OFF, Theme.HOVER, next), Ui.lerpColor(Theme.BORDER_SOFT, if (accent) Theme.ACCENT else Theme.TEXT_FAINT, next), 4)
        g.text(font, label, x + 12, y + 5, Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, next), false)
        return next
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        focusedRow = -1
        val addW = Fonts.width(ADD) + 24
        if (Ui.inBounds(event.x, event.y, panelX + panelW - PAD - addW, panelY + 12, addW, BTN_H)) {
            shortcuts.add(SkyCoreConfig.CommandShortcutEntry())
            SkyCoreConfig.save()
            return true
        }
        val backW = Fonts.width(BACK) + 24
        if (Ui.inBounds(event.x, event.y, panelX + PAD, panelY + panelH - 30, backW, BTN_H)) {
            onClose()
            return true
        }
        val listY = panelY + 52
        val listH = panelH - 52 - 40
        var y = listY - scroll.roundToInt()
        for ((index, entry) in shortcuts.withIndex()) {
            val x = panelX + PAD
            val w = panelW - PAD * 2
            if (y + ROW_H >= listY && y <= listY + listH && Ui.inBounds(event.x, event.y, x, y, w, ROW_H)) {
                val delW = Fonts.width(DELETE) + 16
                val delX = x + w - 8 - delW
                if (Ui.inBounds(event.x, event.y, delX, y + 19, delW, BTN_H)) {
                    shortcuts.removeAt(index)
                    SkyCoreConfig.save()
                    return true
                }
                val fieldW = (w - 100) / 2
                if (Ui.inBounds(event.x, event.y, x + 10, y + 20, fieldW, 22)) {
                    focusedRow = index
                    focusedField = 0
                    return true
                }
                if (Ui.inBounds(event.x, event.y, x + 16 + fieldW, y + 20, fieldW, 22)) {
                    focusedRow = index
                    focusedField = 1
                    return true
                }
            }
            y += ROW_H + 6
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        scroll = (scroll - vertical * 24).toFloat().coerceAtLeast(0f)
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (focusedRow < 0 || focusedRow !in shortcuts.indices) return super.charTyped(event)
        val ch = event.codepointAsString()
        if (ch.isEmpty() || ch[0] < ' ') return true
        val entry = shortcuts[focusedRow]
        if (focusedField == 0) entry.shortcut += ch else entry.message += ch
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (focusedRow in shortcuts.indices) {
            val entry = shortcuts[focusedRow]
            when (event.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (focusedField == 0) {
                        if (entry.shortcut.isNotEmpty()) entry.shortcut = entry.shortcut.dropLast(1)
                    } else if (entry.message.isNotEmpty()) {
                        entry.message = entry.message.dropLast(1)
                    }
                    return true
                }
                GLFW.GLFW_KEY_TAB -> {
                    focusedField = 1 - focusedField
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    focusedRow = -1
                    SkyCoreConfig.save()
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    focusedRow = -1
                    return true
                }
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }
}
