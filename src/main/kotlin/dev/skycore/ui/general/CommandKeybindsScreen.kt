package dev.skycore.ui.general

import com.mojang.blaze3d.platform.InputConstants
import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.general.CommandKeybinds
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

class CommandKeybindsScreen(private val parent: Screen?) : Screen(Component.literal("Command Keybinds")) {

    private companion object {
        val TITLE = Fonts.label("Command Keybinds", Fonts.SEMIBOLD)
        val HINT = Fonts.label("Bind keys to send chat commands", Fonts.SMALL)
        val ADD = Fonts.label("Add", Fonts.SMALL)
        val EDIT = Fonts.label("Edit", Fonts.SMALL)
        val DELETE = Fonts.label("Delete", Fonts.SMALL)
        val BACK = Fonts.label("Back", Fonts.SMALL)
        val SAVE = Fonts.label("Save", Fonts.SMALL)
        const val PAD = 16
        const val ROW_H = 36
        const val BTN_H = 18
    }

    private var editing: SkyCoreConfig.CommandBind? = null
    private var scroll = 0f
    private var capturingKey = false
    private var focusedField = -1
    private var nameBuf = ""
    private var commandBuf = ""
    private var islandBuf = ""
    private var caretBlink = 0f
    private var lastFrameNanos = 0L
    private var addHover = 0f
    private var backHover = 0f

    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        layout()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        layout()
    }

    private fun layout() {
        panelW = (width - 80).coerceIn(420, 720)
        panelH = (height - 60).coerceIn(320, 520)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    override fun onClose() {
        CommandKeybinds.save()
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

        val edit = editing
        if (edit != null) {
            drawEditor(g, edit, mouseX, mouseY, dt)
        } else {
            drawList(g, mouseX, mouseY, dt)
        }
    }

    private fun drawList(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        g.text(font, TITLE, panelX + PAD, panelY + 14, Theme.TEXT, false)
        g.text(font, HINT, panelX + PAD, panelY + 30, Theme.TEXT_MUTED, false)

        val addW = Fonts.width(ADD) + 24
        val addX = panelX + panelW - PAD - addW
        addHover = drawButton(g, ADD, addX, panelY + 12, addW, addHover, mouseX, mouseY, dt, true)

        val listY = panelY + 52
        val listH = panelH - 52 - 40
        val binds = CommandKeybinds.binds()
        val contentH = binds.size * (ROW_H + 6)
        val maxScroll = max(0f, (contentH - listH).toFloat())
        scroll = scroll.coerceIn(0f, maxScroll)

        g.enableScissor(panelX + 1, listY, panelX + panelW - 1, listY + listH)
        var y = listY - scroll.roundToInt()
        for ((index, bind) in binds.withIndex()) {
            drawRow(g, bind, index, panelX + PAD, y, panelW - PAD * 2, mouseX, mouseY)
            y += ROW_H + 6
        }
        g.disableScissor()

        val backW = Fonts.width(BACK) + 24
        backHover = drawButton(g, BACK, panelX + PAD, panelY + panelH - 30, backW, backHover, mouseX, mouseY, dt, false)
    }

    private fun drawRow(
        g: GuiGraphicsExtractor,
        bind: SkyCoreConfig.CommandBind,
        index: Int,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, ROW_H)
        Ui.panel(
            g, x, y, w, ROW_H,
            if (hovered) Theme.HOVER else Theme.SURFACE_RAISED,
            if (bind.enabled) Theme.ACCENT else Theme.BORDER_SOFT,
            4
        )
        val name = Fonts.label(bind.name.ifBlank { "Untitled" }, Fonts.MEDIUM)
        val key = Fonts.label(keyLabel(bind.keyCode), Fonts.SMALL)
        val cmd = Fonts.label(bind.command.ifBlank { "(no command)" }, Fonts.SMALL)
        g.text(font, name, x + 10, y + 7, Theme.TEXT, false)
        g.text(font, key, x + 10 + Fonts.width(name) + 10, y + 8, Theme.ACCENT, false)
        g.text(font, cmd, x + 10, y + 20, Theme.TEXT_MUTED, false)

        val delW = Fonts.width(DELETE) + 16
        val editW = Fonts.width(EDIT) + 16
        val delX = x + w - 8 - delW
        val editX = delX - 6 - editW
        Ui.panel(g, editX, y + 9, editW, BTN_H, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 3)
        g.text(font, EDIT, editX + 8, y + 13, Theme.TEXT_MUTED, false)
        Ui.panel(g, delX, y + 9, delW, BTN_H, Theme.CONTROL_OFF, Theme.DANGER, 3)
        g.text(font, DELETE, delX + 8, y + 13, Theme.DANGER, false)
    }

    private fun drawEditor(
        g: GuiGraphicsExtractor,
        bind: SkyCoreConfig.CommandBind,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ) {
        g.text(font, Fonts.label("Edit Keybind", Fonts.SEMIBOLD), panelX + PAD, panelY + 14, Theme.TEXT, false)

        var y = panelY + 48
        y = drawField(g, "Name", nameBuf, 0, panelX + PAD, y, panelW - PAD * 2, mouseX, mouseY)
        y = drawField(g, "Command", commandBuf, 1, panelX + PAD, y, panelW - PAD * 2, mouseX, mouseY)
        y = drawField(g, "Island Filter", islandBuf, 2, panelX + PAD, y, panelW - PAD * 2, mouseX, mouseY)

        val keyLabel = if (capturingKey) "Press a key..." else keyLabel(bind.keyCode)
        val keyComp = Fonts.label(keyLabel, Fonts.SMALL)
        val keyW = max(120, Fonts.width(keyComp) + 24)
        g.text(font, Fonts.label("Key", Fonts.SMALL), panelX + PAD, y + 5, Theme.TEXT_MUTED, false)
        Ui.panel(
            g, panelX + PAD + 110, y, keyW, 22,
            Theme.CONTROL_OFF,
            if (capturingKey) Theme.ACCENT else Theme.BORDER_SOFT,
            4
        )
        g.text(font, keyComp, panelX + PAD + 122, y + 7, if (capturingKey) Theme.ACCENT else Theme.TEXT, false)
        y += 30

        y = drawToggle(g, "Enabled", bind.enabled, panelX + PAD, y, mouseX, mouseY)
        y = drawToggle(g, "Allow in GUI", bind.allowInGui, panelX + PAD, y, mouseX, mouseY)

        g.text(font, Fonts.label("Modifier", Fonts.SMALL), panelX + PAD, y + 5, Theme.TEXT_MUTED, false)
        val mod = Fonts.label(bind.modifier, Fonts.SMALL)
        val modW = Fonts.width(mod) + 24
        Ui.panel(g, panelX + PAD + 110, y, modW, 22, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 4)
        g.text(font, mod, panelX + PAD + 122, y + 7, Theme.TEXT, false)
        y += 36

        val saveW = Fonts.width(SAVE) + 24
        val backW = Fonts.width(BACK) + 24
        drawButton(g, SAVE, panelX + panelW - PAD - saveW, panelY + panelH - 30, saveW, 1f, mouseX, mouseY, dt, true)
        drawButton(g, BACK, panelX + PAD, panelY + panelH - 30, backW, 1f, mouseX, mouseY, dt, false)
    }

    private fun drawField(
        g: GuiGraphicsExtractor,
        label: String,
        value: String,
        fieldId: Int,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int
    ): Int {
        g.text(font, Fonts.label(label, Fonts.SMALL), x, y, Theme.TEXT_MUTED, false)
        val focused = focusedField == fieldId
        Ui.panel(
            g, x, y + 14, w, 22,
            Theme.CONTROL_OFF,
            if (focused) Theme.ACCENT else Theme.BORDER_SOFT,
            4
        )
        val text = Fonts.label(value.ifEmpty { " " }, Fonts.REGULAR)
        g.text(font, Fonts.label(value, Fonts.REGULAR), x + 8, y + 20, Theme.TEXT, false)
        if (focused && caretBlink < 0.5f) {
            val caretX = x + 8 + Fonts.width(Fonts.label(value, Fonts.REGULAR))
            g.fill(caretX, y + 18, caretX + 1, y + 32, Theme.ACCENT)
        }
        return y + 44
    }

    private fun drawToggle(
        g: GuiGraphicsExtractor,
        label: String,
        enabled: Boolean,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ): Int {
        val title = Fonts.label(label, Fonts.SMALL)
        val w = Fonts.width(title) + 28
        Ui.panel(
            g, x, y, w, 22,
            if (enabled) Theme.CONTROL_ON else Theme.CONTROL_OFF,
            if (enabled) Theme.ACCENT else Theme.BORDER_SOFT,
            4
        )
        val box = 12
        Ui.roundedRect(g, x + 4, y + 5, box, box, Theme.CONTROL_OFF, 3)
        Ui.frame(g, x + 4, y + 5, box, box, if (enabled) Theme.ACCENT else Theme.BORDER_SOFT, 3)
        if (enabled) Ui.check(g, x + 6, y + 8, Theme.ACCENT)
        g.text(font, title, x + 20, y + 7, Theme.TEXT, false)
        return y + 30
    }

    private fun drawButton(
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

    private fun keyLabel(keyCode: Int): String {
        if (keyCode < 0) return "Unbound"
        return runCatching {
            InputConstants.Type.KEYSYM.getOrCreate(keyCode).displayName.string
        }.getOrElse { "Key $keyCode" }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        val edit = editing
        if (edit != null) {
            return editorClick(event, edit)
        }
        return listClick(event)
    }

    private fun listClick(event: MouseButtonEvent): Boolean {
        val addW = Fonts.width(ADD) + 24
        val addX = panelX + panelW - PAD - addW
        if (Ui.inBounds(event.x, event.y, addX, panelY + 12, addW, BTN_H)) {
            openEditor(CommandKeybinds.addBind())
            return true
        }
        val backW = Fonts.width(BACK) + 24
        if (Ui.inBounds(event.x, event.y, panelX + PAD, panelY + panelH - 30, backW, BTN_H)) {
            onClose()
            return true
        }
        val listY = panelY + 52
        val listH = panelH - 52 - 40
        val binds = CommandKeybinds.binds()
        var y = listY - scroll.roundToInt()
        for ((index, bind) in binds.withIndex()) {
            val x = panelX + PAD
            val w = panelW - PAD * 2
            if (y + ROW_H >= listY && y <= listY + listH && Ui.inBounds(event.x, event.y, x, y, w, ROW_H)) {
                val delW = Fonts.width(DELETE) + 16
                val editW = Fonts.width(EDIT) + 16
                val delX = x + w - 8 - delW
                val editX = delX - 6 - editW
                if (Ui.inBounds(event.x, event.y, delX, y + 9, delW, BTN_H)) {
                    CommandKeybinds.removeBind(index)
                    return true
                }
                if (Ui.inBounds(event.x, event.y, editX, y + 9, editW, BTN_H) ||
                    Ui.inBounds(event.x, event.y, x, y, w, ROW_H)
                ) {
                    openEditor(bind)
                    return true
                }
            }
            y += ROW_H + 6
        }
        return true
    }

    private fun editorClick(event: MouseButtonEvent, bind: SkyCoreConfig.CommandBind): Boolean {
        focusedField = -1
        capturingKey = false
        var y = panelY + 48
        val fieldW = panelW - PAD * 2
        if (Ui.inBounds(event.x, event.y, panelX + PAD, y + 14, fieldW, 22)) {
            focusedField = 0
            return true
        }
        y += 44
        if (Ui.inBounds(event.x, event.y, panelX + PAD, y + 14, fieldW, 22)) {
            focusedField = 1
            return true
        }
        y += 44
        if (Ui.inBounds(event.x, event.y, panelX + PAD, y + 14, fieldW, 22)) {
            focusedField = 2
            return true
        }
        y += 44
        val keyComp = Fonts.label(keyLabel(bind.keyCode), Fonts.SMALL)
        val keyW = max(120, Fonts.width(keyComp) + 24)
        if (Ui.inBounds(event.x, event.y, panelX + PAD + 110, y, keyW, 22)) {
            capturingKey = true
            return true
        }
        y += 30
        val enTitle = Fonts.label("Enabled", Fonts.SMALL)
        val enW = Fonts.width(enTitle) + 28
        if (Ui.inBounds(event.x, event.y, panelX + PAD, y, enW, 22)) {
            bind.enabled = !bind.enabled
            return true
        }
        y += 30
        val guiTitle = Fonts.label("Allow in GUI", Fonts.SMALL)
        val guiW = Fonts.width(guiTitle) + 28
        if (Ui.inBounds(event.x, event.y, panelX + PAD, y, guiW, 22)) {
            bind.allowInGui = !bind.allowInGui
            return true
        }
        y += 30
        val mod = Fonts.label(bind.modifier, Fonts.SMALL)
        val modW = Fonts.width(mod) + 24
        if (Ui.inBounds(event.x, event.y, panelX + PAD + 110, y, modW, 22)) {
            val values = CommandKeybinds.Modifier.entries
            val idx = values.indexOfFirst { it.name.equals(bind.modifier, true) }.let { if (it < 0) 0 else it }
            bind.modifier = values[(idx + 1) % values.size].name
            return true
        }
        val saveW = Fonts.width(SAVE) + 24
        val backW = Fonts.width(BACK) + 24
        if (Ui.inBounds(event.x, event.y, panelX + panelW - PAD - saveW, panelY + panelH - 30, saveW, BTN_H)) {
            commitEditor(bind)
            editing = null
            return true
        }
        if (Ui.inBounds(event.x, event.y, panelX + PAD, panelY + panelH - 30, backW, BTN_H)) {
            commitEditor(bind)
            editing = null
            return true
        }
        return true
    }

    private fun openEditor(bind: SkyCoreConfig.CommandBind) {
        editing = bind
        nameBuf = bind.name
        commandBuf = bind.command
        islandBuf = bind.islandFilter
        focusedField = -1
        capturingKey = false
    }

    private fun commitEditor(bind: SkyCoreConfig.CommandBind) {
        bind.name = nameBuf
        bind.command = commandBuf
        bind.islandFilter = islandBuf
        CommandKeybinds.save()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (editing == null) {
            scroll = (scroll - vertical * 24).toFloat().coerceAtLeast(0f)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (editing == null || capturingKey || focusedField < 0) return super.charTyped(event)
        val ch = event.codepointAsString()
        if (ch.isEmpty() || ch[0] < ' ') return true
        when (focusedField) {
            0 -> nameBuf += ch
            1 -> commandBuf += ch
            2 -> islandBuf += ch
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (capturingKey && editing != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                editing!!.keyCode = -1
            } else {
                editing!!.keyCode = event.key()
            }
            capturingKey = false
            return true
        }
        if (focusedField >= 0 && editing != null) {
            when (event.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    when (focusedField) {
                        0 -> if (nameBuf.isNotEmpty()) nameBuf = nameBuf.dropLast(1)
                        1 -> if (commandBuf.isNotEmpty()) commandBuf = commandBuf.dropLast(1)
                        2 -> if (islandBuf.isNotEmpty()) islandBuf = islandBuf.dropLast(1)
                    }
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    focusedField = -1
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    focusedField = -1
                    return true
                }
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editing != null) {
                commitEditor(editing!!)
                editing = null
                return true
            }
            onClose()
            return true
        }
        return super.keyPressed(event)
    }
}
