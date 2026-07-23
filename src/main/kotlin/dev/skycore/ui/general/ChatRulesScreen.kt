package dev.skycore.ui.general

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.general.ChatRules
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

class ChatRulesScreen(private val parent: Screen?) : Screen(Component.literal("Chat Rules")) {

    private companion object {
        val TITLE = Fonts.label("Chat Rules", Fonts.SEMIBOLD)
        val HINT = Fonts.label("Match chat messages and fire alerts", Fonts.SMALL)
        val ADD = Fonts.label("Add", Fonts.SMALL)
        val EDIT = Fonts.label("Edit", Fonts.SMALL)
        val DELETE = Fonts.label("Delete", Fonts.SMALL)
        val BACK = Fonts.label("Back", Fonts.SMALL)
        val SAVE = Fonts.label("Save", Fonts.SMALL)
        val MATCH_TYPES = listOf("Equals", "Contains", "StartsWith", "EndsWith", "Regex")
        const val PAD = 16
        const val ROW_H = 36
        const val BTN_H = 18
    }

    private val rules get() = ChatRules.rules()

    private var editing: SkyCoreConfig.ChatRuleEntry? = null
    private var scroll = 0f
    private var focusedField = -1
    private var nameBuf = ""
    private var matchBuf = ""
    private var titleBuf = ""
    private var soundBuf = ""
    private var islandBuf = ""
    private var classBuf = ""
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
        panelW = (width - 80).coerceIn(440, 760)
        panelH = (height - 50).coerceIn(360, 560)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
    }

    override fun onClose() {
        ChatRules.save()
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
        if (editing != null) drawEditor(g, editing!!, mouseX, mouseY, dt) else drawList(g, mouseX, mouseY, dt)
    }

    private fun drawList(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        g.text(font, TITLE, panelX + PAD, panelY + 14, Theme.TEXT, false)
        g.text(font, HINT, panelX + PAD, panelY + 30, Theme.TEXT_MUTED, false)
        val addW = Fonts.width(ADD) + 24
        val addX = panelX + panelW - PAD - addW
        addHover = button(g, ADD, addX, panelY + 12, addW, addHover, mouseX, mouseY, dt, true)
        val listY = panelY + 52
        val listH = panelH - 52 - 40
        val contentH = rules.size * (ROW_H + 6)
        scroll = scroll.coerceIn(0f, max(0f, (contentH - listH).toFloat()))
        g.enableScissor(panelX + 1, listY, panelX + panelW - 1, listY + listH)
        var y = listY - scroll.roundToInt()
        for ((index, rule) in rules.withIndex()) {
            val x = panelX + PAD
            val w = panelW - PAD * 2
            val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, ROW_H)
            Ui.panel(g, x, y, w, ROW_H, if (hovered) Theme.HOVER else Theme.SURFACE_RAISED, if (rule.enabled) Theme.ACCENT else Theme.BORDER_SOFT, 4)
            g.text(font, Fonts.label(rule.name.ifBlank { "Untitled" }, Fonts.MEDIUM), x + 10, y + 7, Theme.TEXT, false)
            g.text(font, Fonts.label("${rule.matchType} · ${rule.match.ifBlank { "(empty)" }}", Fonts.SMALL), x + 10, y + 20, Theme.TEXT_MUTED, false)
            val delW = Fonts.width(DELETE) + 16
            val editW = Fonts.width(EDIT) + 16
            val delX = x + w - 8 - delW
            val editX = delX - 6 - editW
            Ui.panel(g, editX, y + 9, editW, BTN_H, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 3)
            g.text(font, EDIT, editX + 8, y + 13, Theme.TEXT_MUTED, false)
            Ui.panel(g, delX, y + 9, delW, BTN_H, Theme.CONTROL_OFF, Theme.DANGER, 3)
            g.text(font, DELETE, delX + 8, y + 13, Theme.DANGER, false)
            y += ROW_H + 6
        }
        g.disableScissor()
        val backW = Fonts.width(BACK) + 24
        backHover = button(g, BACK, panelX + PAD, panelY + panelH - 30, backW, backHover, mouseX, mouseY, dt, false)
    }

    private fun drawEditor(g: GuiGraphicsExtractor, rule: SkyCoreConfig.ChatRuleEntry, mouseX: Int, mouseY: Int, dt: Float) {
        g.text(font, Fonts.label("Edit Rule", Fonts.SEMIBOLD), panelX + PAD, panelY + 14, Theme.TEXT, false)
        var y = panelY + 42
        y = field(g, "Name", nameBuf, 0, panelX + PAD, y, panelW - PAD * 2)
        y = field(g, "Match", matchBuf, 1, panelX + PAD, y, panelW - PAD * 2)
        g.text(font, Fonts.label("Match Type", Fonts.SMALL), panelX + PAD, y + 5, Theme.TEXT_MUTED, false)
        val type = Fonts.label(rule.matchType, Fonts.SMALL)
        val typeW = Fonts.width(type) + 24
        Ui.panel(g, panelX + PAD + 110, y, typeW, 22, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 4)
        g.text(font, type, panelX + PAD + 122, y + 7, Theme.TEXT, false)
        y += 30
        y = toggle(g, "Enabled", rule.enabled, panelX + PAD, y)
        y = toggle(g, "Case Sensitive", rule.caseSensitive, panelX + PAD, y)
        y = toggle(g, "Cancel Message", rule.cancel, panelX + PAD, y)
        y = field(g, "Title", titleBuf, 2, panelX + PAD, y, panelW - PAD * 2)
        y = field(g, "Sound", soundBuf, 3, panelX + PAD, y, panelW - PAD * 2)
        y = field(g, "Island Filter", islandBuf, 4, panelX + PAD, y, panelW - PAD * 2)
        y = field(g, "Class Filter", classBuf, 5, panelX + PAD, y, panelW - PAD * 2)
        val saveW = Fonts.width(SAVE) + 24
        val backW = Fonts.width(BACK) + 24
        button(g, SAVE, panelX + panelW - PAD - saveW, panelY + panelH - 30, saveW, 1f, mouseX, mouseY, dt, true)
        button(g, BACK, panelX + PAD, panelY + panelH - 30, backW, 1f, mouseX, mouseY, dt, false)
    }

    private fun field(g: GuiGraphicsExtractor, label: String, value: String, id: Int, x: Int, y: Int, w: Int): Int {
        g.text(font, Fonts.label(label, Fonts.SMALL), x, y, Theme.TEXT_MUTED, false)
        val focused = focusedField == id
        Ui.panel(g, x, y + 12, w, 20, Theme.CONTROL_OFF, if (focused) Theme.ACCENT else Theme.BORDER_SOFT, 4)
        g.text(font, Fonts.label(value, Fonts.REGULAR), x + 8, y + 17, Theme.TEXT, false)
        if (focused && caretBlink < 0.5f) {
            val caretX = x + 8 + Fonts.width(Fonts.label(value, Fonts.REGULAR))
            g.fill(caretX, y + 15, caretX + 1, y + 27, Theme.ACCENT)
        }
        return y + 38
    }

    private fun toggle(g: GuiGraphicsExtractor, label: String, enabled: Boolean, x: Int, y: Int): Int {
        val title = Fonts.label(label, Fonts.SMALL)
        val w = Fonts.width(title) + 28
        Ui.panel(g, x, y, w, 20, if (enabled) Theme.CONTROL_ON else Theme.CONTROL_OFF, if (enabled) Theme.ACCENT else Theme.BORDER_SOFT, 4)
        Ui.roundedRect(g, x + 4, y + 4, 12, 12, Theme.CONTROL_OFF, 3)
        Ui.frame(g, x + 4, y + 4, 12, 12, if (enabled) Theme.ACCENT else Theme.BORDER_SOFT, 3)
        if (enabled) Ui.check(g, x + 6, y + 7, Theme.ACCENT)
        g.text(font, title, x + 20, y + 6, Theme.TEXT, false)
        return y + 26
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
        val edit = editing
        if (edit != null) return editorClick(event, edit)
        return listClick(event)
    }

    private fun listClick(event: MouseButtonEvent): Boolean {
        val addW = Fonts.width(ADD) + 24
        if (Ui.inBounds(event.x, event.y, panelX + panelW - PAD - addW, panelY + 12, addW, BTN_H)) {
            val rule = SkyCoreConfig.ChatRuleEntry()
            rules.add(rule)
            ChatRules.save()
            open(rule)
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
        for ((index, rule) in rules.withIndex()) {
            val x = panelX + PAD
            val w = panelW - PAD * 2
            if (y + ROW_H >= listY && y <= listY + listH && Ui.inBounds(event.x, event.y, x, y, w, ROW_H)) {
                val delW = Fonts.width(DELETE) + 16
                val editW = Fonts.width(EDIT) + 16
                val delX = x + w - 8 - delW
                val editX = delX - 6 - editW
                if (Ui.inBounds(event.x, event.y, delX, y + 9, delW, BTN_H)) {
                    rules.removeAt(index)
                    ChatRules.save()
                    return true
                }
                open(rule)
                return true
            }
            y += ROW_H + 6
        }
        return true
    }

    private fun editorClick(event: MouseButtonEvent, rule: SkyCoreConfig.ChatRuleEntry): Boolean {
        focusedField = -1
        var y = panelY + 42
        val w = panelW - PAD * 2
        if (hitField(event, y)) { focusedField = 0; return true }
        y += 38
        if (hitField(event, y)) { focusedField = 1; return true }
        y += 38
        val type = Fonts.label(rule.matchType, Fonts.SMALL)
        val typeW = Fonts.width(type) + 24
        if (Ui.inBounds(event.x, event.y, panelX + PAD + 110, y, typeW, 22)) {
            val idx = MATCH_TYPES.indexOf(rule.matchType).let { if (it < 0) 0 else it }
            rule.matchType = MATCH_TYPES[(idx + 1) % MATCH_TYPES.size]
            return true
        }
        y += 30
        if (hitToggle(event, "Enabled", y)) { rule.enabled = !rule.enabled; return true }
        y += 26
        if (hitToggle(event, "Case Sensitive", y)) { rule.caseSensitive = !rule.caseSensitive; return true }
        y += 26
        if (hitToggle(event, "Cancel Message", y)) { rule.cancel = !rule.cancel; return true }
        y += 26
        if (hitField(event, y)) { focusedField = 2; return true }
        y += 38
        if (hitField(event, y)) { focusedField = 3; return true }
        y += 38
        if (hitField(event, y)) { focusedField = 4; return true }
        y += 38
        if (hitField(event, y)) { focusedField = 5; return true }
        val saveW = Fonts.width(SAVE) + 24
        val backW = Fonts.width(BACK) + 24
        if (Ui.inBounds(event.x, event.y, panelX + panelW - PAD - saveW, panelY + panelH - 30, saveW, BTN_H) ||
            Ui.inBounds(event.x, event.y, panelX + PAD, panelY + panelH - 30, backW, BTN_H)
        ) {
            commit(rule)
            editing = null
            return true
        }
        return true
    }

    private fun hitField(event: MouseButtonEvent, y: Int): Boolean =
        Ui.inBounds(event.x, event.y, panelX + PAD, y + 12, panelW - PAD * 2, 20)

    private fun hitToggle(event: MouseButtonEvent, label: String, y: Int): Boolean {
        val w = Fonts.width(Fonts.label(label, Fonts.SMALL)) + 28
        return Ui.inBounds(event.x, event.y, panelX + PAD, y, w, 20)
    }

    private fun open(rule: SkyCoreConfig.ChatRuleEntry) {
        editing = rule
        nameBuf = rule.name
        matchBuf = rule.match
        titleBuf = rule.title
        soundBuf = rule.sound
        islandBuf = rule.islandFilter
        classBuf = rule.classFilter
        focusedField = -1
    }

    private fun commit(rule: SkyCoreConfig.ChatRuleEntry) {
        rule.name = nameBuf
        rule.match = matchBuf
        rule.title = titleBuf
        rule.sound = soundBuf
        rule.islandFilter = islandBuf
        rule.classFilter = classBuf
        ChatRules.save()
    }

    private fun buf(): StringBuilder {
        val s = when (focusedField) {
            0 -> nameBuf
            1 -> matchBuf
            2 -> titleBuf
            3 -> soundBuf
            4 -> islandBuf
            5 -> classBuf
            else -> return StringBuilder()
        }
        return StringBuilder(s)
    }

    private fun setBuf(value: String) {
        when (focusedField) {
            0 -> nameBuf = value
            1 -> matchBuf = value
            2 -> titleBuf = value
            3 -> soundBuf = value
            4 -> islandBuf = value
            5 -> classBuf = value
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (editing == null) {
            scroll = (scroll - vertical * 24).toFloat().coerceAtLeast(0f)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (editing == null || focusedField < 0) return super.charTyped(event)
        val ch = event.codepointAsString()
        if (ch.isEmpty() || ch[0] < ' ') return true
        setBuf(buf().append(ch).toString())
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (focusedField >= 0 && editing != null) {
            when (event.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    val cur = buf().toString()
                    if (cur.isNotEmpty()) setBuf(cur.dropLast(1))
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                    focusedField = -1
                    return true
                }
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editing != null) {
                commit(editing!!)
                editing = null
                return true
            }
            onClose()
            return true
        }
        return super.keyPressed(event)
    }
}
