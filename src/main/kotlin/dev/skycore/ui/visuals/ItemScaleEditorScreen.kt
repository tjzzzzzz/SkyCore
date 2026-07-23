package dev.skycore.ui.visuals

import dev.skycore.config.SkyCoreConfig
import dev.skycore.ui.render.Fonts
import dev.skycore.ui.render.Icons
import dev.skycore.ui.render.Ui
import dev.skycore.ui.theme.Theme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class ItemScaleEditorScreen(private val parent: Screen?) : Screen(Component.literal("Item Scale Editor")) {

    private companion object {
        val TITLE: Component = Fonts.label("Item Scale & Animation", Fonts.SEMIBOLD)
        val HINT: Component = Fonts.label("Hold an item  ·  Drag sliders  ·  Esc saves", Fonts.SMALL)
        val RESET_ALL: Component = Fonts.label("Reset All", Fonts.SMALL)
        val TEST_SWING: Component = Fonts.label("Test Swing", Fonts.SMALL)

        const val PANEL_PAD = 14
        const val ROW_H = 22
        const val SLIDER_W = 118
        const val LABEL_W = 52
        const val VALUE_W = 34
        const val RESET_W = 12
        const val CELL_W = LABEL_W + 6 + SLIDER_W + 6 + VALUE_W + 6 + RESET_W
        const val CELL_GAP = 14
        const val TOGGLE_H = 18
    }

    private class Slider(
        val title: Component,
        val min: Float,
        val max: Float,
        val step: Float,
        val default: Float,
        private val getter: () -> Float,
        private val setter: (Float) -> Unit,
        private val format: (Float) -> String
    ) {
        var hover = 0f
        private var cachedText = ""
        private var cachedLabel: Component = Fonts.label("", Fonts.SMALL)

        var value: Float
            get() = getter()
            set(v) = setter(snap(v))

        val valueLabel: Component
            get() {
                val text = format(value)
                if (text != cachedText) {
                    cachedText = text
                    cachedLabel = Fonts.label(text, Fonts.SMALL)
                }
                return cachedLabel
            }

        fun progress(): Float {
            if (max <= min) return 0f
            return ((value - min) / (max - min)).coerceIn(0f, 1f)
        }

        fun setFromProgress(progress: Float) {
            value = min + (max - min) * progress.coerceIn(0f, 1f)
        }

        fun reset() {
            value = default
        }

        private fun snap(raw: Float): Float {
            val clamped = raw.coerceIn(min, max)
            if (step <= 0f) return clamped
            val steps = kotlin.math.round((clamped - min) / step)
            return (min + steps * step).coerceIn(min, max)
        }
    }

    private class Toggle(
        val title: Component,
        val default: Boolean,
        private val getter: () -> Boolean,
        private val setter: (Boolean) -> Unit
    ) {
        var hover = 0f
        var knob = if (getter()) 1f else 0f

        var enabled: Boolean
            get() = getter()
            set(v) = setter(v)

        fun reset() {
            enabled = default
        }
    }

    private val opts get() = SkyCoreConfig.instance.itemScaleAnimation

    private val sliders = listOf(
        Slider(Fonts.label("Size", Fonts.SMALL), 0.1f, 2f, 0.05f, 1f, { opts.size }, { opts.size = it }) {
            "%.2f".format(it)
        },
        Slider(Fonts.label("X", Fonts.SMALL), -2f, 2f, 0.05f, 0f, { opts.x }, { opts.x = it }) {
            "%.2f".format(it)
        },
        Slider(Fonts.label("Y", Fonts.SMALL), -2f, 2f, 0.05f, 0f, { opts.y }, { opts.y = it }) {
            "%.2f".format(it)
        },
        Slider(Fonts.label("Z", Fonts.SMALL), -2f, 2f, 0.05f, 0f, { opts.z }, { opts.z = it }) {
            "%.2f".format(it)
        },
        Slider(Fonts.label("Yaw", Fonts.SMALL), -180f, 180f, 1f, 0f, { opts.yaw }, { opts.yaw = it }) {
            it.toInt().toString()
        },
        Slider(Fonts.label("Pitch", Fonts.SMALL), -180f, 180f, 1f, 0f, { opts.pitch }, { opts.pitch = it }) {
            it.toInt().toString()
        },
        Slider(Fonts.label("Roll", Fonts.SMALL), -180f, 180f, 1f, 0f, { opts.roll }, { opts.roll = it }) {
            it.toInt().toString()
        },
        Slider(Fonts.label("Speed", Fonts.SMALL), 1f, 32f, 1f, 6f, { opts.speed }, { opts.speed = it }) {
            it.toInt().toString()
        }
    )

    private val toggles = listOf(
        Toggle(Fonts.label("Ignore Effects", Fonts.SMALL), false, { opts.ignoreEffects }, { opts.ignoreEffects = it }),
        Toggle(Fonts.label("No Swing", Fonts.SMALL), false, { opts.noSwing }, { opts.noSwing = it }),
        Toggle(Fonts.label("No Equip Reset", Fonts.SMALL), false, { opts.noEquipReset }, { opts.noEquipReset = it }),
        Toggle(Fonts.label("Disable Re-Swing", Fonts.SMALL), true, { opts.disableReSwing }, { opts.disableReSwing = it })
    )

    private var dragging: Slider? = null
    private var dragTrackX = 0
    private var dragTrackW = 0
    private var resetAllHover = 0f
    private var testSwingHover = 0f
    private var lastFrameNanos = 0L

    private var panelX = 0
    private var panelY = 12
    private var panelW = 0
    private var panelH = 0
    private var cols = 4

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        if (!opts.enabled) opts.enabled = true
        layoutPanel()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        layoutPanel()
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    private fun layoutPanel() {
        cols = when {
            width >= 1100 -> 4
            width >= 820 -> 3
            else -> 2
        }
        val sliderRows = (sliders.size + cols - 1) / cols
        panelW = (cols * CELL_W + (cols - 1) * CELL_GAP + PANEL_PAD * 2).coerceAtMost(width - 24)
        panelH = PANEL_PAD + 28 + sliderRows * ROW_H + 10 + TOGGLE_H + PANEL_PAD
        panelX = (width - panelW) / 2
        panelY = 12
    }

    override fun onClose() {
        SkyCoreConfig.save()
        val back = parent
        if (back != null) minecraft.setScreenAndShow(back) else super.onClose()
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = now

        Ui.shadow(g, panelX, panelY, panelW, panelH, 5)
        Ui.panel(g, panelX, panelY, panelW, panelH, Theme.SURFACE, Theme.BORDER, 6)

        g.text(font, TITLE, panelX + PANEL_PAD, panelY + 10, Theme.TEXT, false)
        g.text(font, HINT, panelX + PANEL_PAD + Fonts.width(TITLE) + 12, panelY + 12, Theme.TEXT_MUTED, false)

        val resetW = Fonts.width(RESET_ALL) + 20
        val testW = Fonts.width(TEST_SWING) + 20
        val resetX = panelX + panelW - PANEL_PAD - resetW
        val testX = resetX - 8 - testW
        val btnY = panelY + 8

        testSwingHover = drawHeaderButton(g, TEST_SWING, testX, btnY, testW, testSwingHover, mouseX, mouseY, dt)
        resetAllHover = drawHeaderButton(g, RESET_ALL, resetX, btnY, resetW, resetAllHover, mouseX, mouseY, dt)

        for ((index, slider) in sliders.withIndex()) {
            val col = index % cols
            val row = index / cols
            val x = panelX + PANEL_PAD + col * (CELL_W + CELL_GAP)
            val y = panelY + 34 + row * ROW_H
            drawSlider(g, slider, x, y, mouseX, mouseY, dt)
        }

        val toggleY = panelY + panelH - PANEL_PAD - TOGGLE_H
        var toggleX = panelX + PANEL_PAD
        for (toggle in toggles) {
            val tw = Fonts.width(toggle.title) + 28
            drawToggle(g, toggle, toggleX, toggleY, tw, mouseX, mouseY, dt)
            toggleX += tw + 10
        }
    }

    private fun drawHeaderButton(
        g: GuiGraphicsExtractor,
        label: Component,
        x: Int,
        y: Int,
        w: Int,
        hover: Float,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ): Float {
        val next = Ui.approach(hover, if (Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, 16)) 1f else 0f, dt)
        Ui.panel(
            g, x, y, w, 16,
            Ui.lerpColor(Theme.CONTROL_OFF, Theme.HOVER, next),
            Ui.lerpColor(Theme.BORDER_SOFT, Theme.ACCENT, next),
            4
        )
        g.text(font, label, x + 10, y + 4, Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, next), false)
        return next
    }

    private fun testSwing() {
        val player = minecraft.player ?: return

        player.swinging = false
        player.swing(InteractionHand.MAIN_HAND)
    }

    private fun drawSlider(
        g: GuiGraphicsExtractor,
        slider: Slider,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ) {
        val trackX = x + LABEL_W + 6
        val valueX = trackX + SLIDER_W + 6
        val resetX = valueX + VALUE_W + 6
        val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, CELL_W, ROW_H - 2)
        slider.hover = Ui.approach(slider.hover, if (hovered || dragging === slider) 1f else 0f, dt)

        g.text(
            font, slider.title, x, y + 5,
            Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, 0.55f + slider.hover * 0.45f), false
        )

        Ui.roundedRect(g, trackX, y + 8, SLIDER_W, 4, Theme.CONTROL_OFF, 2)
        val fill = (SLIDER_W * slider.progress()).roundToInt().coerceAtLeast(0)
        if (fill > 0) Ui.roundedRect(g, trackX, y + 8, fill, 4, Theme.ACCENT, 2)
        val knobX = trackX + fill - 3
        Ui.roundedRect(g, knobX, y + 5, 7, 10, Theme.TEXT, 3)
        if (dragging === slider) {
            Ui.roundedRect(g, knobX - 1, y + 4, 9, 12, Ui.withAlpha(Theme.ACCENT, 0.25f), 4)
        }

        val value = slider.valueLabel
        g.text(font, value, valueX + VALUE_W - Fonts.width(value), y + 5, Theme.TEXT_MUTED, false)

        val resetHover = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), resetX - 2, y + 3, RESET_W + 4, 14)
        Icons.reset(g, resetX, y + 5, if (resetHover) Theme.ACCENT else Theme.TEXT_FAINT)
    }

    private fun drawToggle(
        g: GuiGraphicsExtractor,
        toggle: Toggle,
        x: Int,
        y: Int,
        w: Int,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ) {
        val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, TOGGLE_H)
        toggle.hover = Ui.approach(toggle.hover, if (hovered) 1f else 0f, dt)
        toggle.knob = Ui.approach(toggle.knob, if (toggle.enabled) 1f else 0f, dt)
        val t = toggle.knob

        Ui.panel(
            g, x, y, w, TOGGLE_H,
            Ui.lerpColor(Theme.CONTROL_OFF, Theme.CONTROL_ON, t),
            Ui.lerpColor(
                Ui.lerpColor(Theme.BORDER_SOFT, Theme.TEXT_FAINT, toggle.hover * 0.6f),
                Theme.ACCENT,
                t
            ),
            4
        )
        val box = 12
        val bx = x + 4
        val by = y + (TOGGLE_H - box) / 2
        Ui.roundedRect(
            g, bx, by, box, box,
            Ui.lerpColor(Theme.CONTROL_OFF, Theme.CONTROL_ON, t),
            3
        )
        Ui.frame(g, bx, by, box, box, Ui.lerpColor(Theme.BORDER_SOFT, Theme.ACCENT, t), 3)
        if (t > 0.02f) Ui.check(g, bx + 2, by + 3, Ui.withAlpha(Theme.ACCENT, t))
        g.text(
            font, toggle.title, x + 20, y + 5,
            Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, 0.4f + t * 0.6f), false
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)

        val resetW = Fonts.width(RESET_ALL) + 20
        val testW = Fonts.width(TEST_SWING) + 20
        val resetX = panelX + panelW - PANEL_PAD - resetW
        val testX = resetX - 8 - testW
        val btnY = panelY + 8

        if (Ui.inBounds(event.x, event.y, testX, btnY, testW, 16)) {
            testSwing()
            return true
        }
        if (Ui.inBounds(event.x, event.y, resetX, btnY, resetW, 16)) {
            for (slider in sliders) slider.reset()
            for (toggle in toggles) toggle.reset()
            return true
        }

        for ((index, slider) in sliders.withIndex()) {
            val col = index % cols
            val row = index / cols
            val x = panelX + PANEL_PAD + col * (CELL_W + CELL_GAP)
            val y = panelY + 34 + row * ROW_H
            val trackX = x + LABEL_W + 6
            val valueX = trackX + SLIDER_W + 6
            val resetX = valueX + VALUE_W + 6

            if (Ui.inBounds(event.x, event.y, resetX - 2, y + 3, RESET_W + 4, 14)) {
                slider.reset()
                return true
            }
            if (Ui.inBounds(event.x, event.y, trackX - 2, y + 2, SLIDER_W + 4, 16)) {
                dragging = slider
                dragTrackX = trackX
                dragTrackW = SLIDER_W
                slider.setFromProgress(((event.x - trackX) / SLIDER_W).toFloat())
                return true
            }
        }

        val toggleY = panelY + panelH - PANEL_PAD - TOGGLE_H
        var toggleX = panelX + PANEL_PAD
        for (toggle in toggles) {
            val tw = Fonts.width(toggle.title) + 28
            if (Ui.inBounds(event.x, event.y, toggleX, toggleY, tw, TOGGLE_H)) {
                toggle.enabled = !toggle.enabled
                return true
            }
            toggleX += tw + 10
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val slider = dragging ?: return super.mouseDragged(event, dragX, dragY)
        if (dragTrackW <= 0) return true
        slider.setFromProgress(((event.x - dragTrackX) / dragTrackW).toFloat())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = null
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }
}
