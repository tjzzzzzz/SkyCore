package dev.skylite.ui.hud

import dev.skylite.config.SkyLiteConfig
import dev.skylite.ui.render.Fonts
import dev.skylite.ui.render.Ui
import dev.skylite.ui.theme.Theme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * drag and scale pass over every hud widget.
 *
 * widgets render exactly as they do in game, with a selection frame and grab
 * handle on top, so what you line up here is what you get.
 */
class HudEditorScreen(private val parent: Screen?) : Screen(Component.literal("HUD Editor")) {

    private companion object {
        val HINT: Component =
            Fonts.label("Drag to move  ·  Scroll to scale  ·  R resets  ·  Esc saves", Fonts.SMALL)
        val TITLE: Component = Fonts.label("Edit HUD", Fonts.SEMIBOLD)
        const val SNAP = 6
    }

    private var dragged: HudWidget? = null
    private var grabX = 0
    private var grabY = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        HudManager.editing = true
    }

    override fun onClose() {
        HudManager.editing = false
        SkyLiteConfig.save()
        // setScreenAndShow will not take null, so falling back to the default
        // close path is the only way to get back to the game
        val back = parent
        if (back != null) minecraft.setScreenAndShow(back) else super.onClose()
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)

        // dim the world so the widgets read clearly against any background
        g.fill(0, 0, width, height, 0x99000000.toInt())

        HudManager.renderAll(g, true)

        for (widget in HudManager.widgets) {
            val x = widget.xFor(width)
            val y = widget.yFor(height)
            val w = (widget.width * widget.scale).roundToInt()
            val h = (widget.height * widget.scale).roundToInt()

            val active = dragged === widget
            val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, h)
            val tint = if (active || hovered) Theme.ACCENT else Ui.withAlpha(Theme.ACCENT, 0.4f)

            Ui.frame(g, x - 2, y - 2, w + 4, h + 4, tint, 4)
            // corner ticks, the usual "this is resizable" affordance
            g.fill(x - 2, y - 2, x + 4, y - 1, tint)
            g.fill(x + w - 4, y + h + 1, x + w + 2, y + h + 2, tint)
        }

        drawToolbar(g)
    }

    private fun drawToolbar(g: GuiGraphicsExtractor) {
        val hintW = Fonts.width(HINT)
        val titleW = Fonts.width(TITLE)
        val boxW = maxOf(hintW, titleW) + 28
        val x = (width - boxW) / 2
        val y = height - 54

        Ui.shadow(g, x, y, boxW, 40, 4)
        Ui.panel(g, x, y, boxW, 40, Theme.SURFACE, Theme.BORDER, 6)
        g.text(font, TITLE, x + 14, y + 9, Theme.TEXT, false)
        g.text(font, HINT, x + 14, y + 24, Theme.TEXT_MUTED, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // topmost first so overlapping widgets pick the one drawn last
            for (widget in HudManager.widgets.asReversed()) {
                val x = widget.xFor(width)
                val y = widget.yFor(height)
                val w = (widget.width * widget.scale).roundToInt()
                val h = (widget.height * widget.scale).roundToInt()
                if (Ui.inBounds(event.x, event.y, x, y, w, h)) {
                    dragged = widget
                    grabX = (event.x - x).roundToInt()
                    grabY = (event.y - y).roundToInt()
                    return true
                }
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val widget = dragged ?: return super.mouseDragged(event, dragX, dragY)

        var px = (event.x - grabX).roundToInt()
        var py = (event.y - grabY).roundToInt()

        // magnet to the screen edges, getting a hud flush to a corner by hand is
        // otherwise a pixel hunting exercise
        val w = (widget.width * widget.scale).roundToInt()
        val h = (widget.height * widget.scale).roundToInt()
        if (px < SNAP) px = 0
        if (py < SNAP) py = 0
        if (px + w > width - SNAP) px = width - w
        if (py + h > height - SNAP) py = height - h

        widget.moveTo(px, py, width, height)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragged = null
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        for (widget in HudManager.widgets.asReversed()) {
            val x = widget.xFor(width)
            val y = widget.yFor(height)
            val w = (widget.width * widget.scale).roundToInt()
            val h = (widget.height * widget.scale).roundToInt()
            if (Ui.inBounds(mouseX, mouseY, x, y, w, h)) {
                widget.scale += (vertical * 0.1f).toFloat()
                return true
            }
        }
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_R) {
            for (widget in HudManager.widgets) {
                widget.scale = 1f
                widget.moveTo((width * 0.01f).toInt(), (height * 0.04f).toInt(), width, height)
            }
            return true
        }
        return super.keyPressed(event)
    }
}
