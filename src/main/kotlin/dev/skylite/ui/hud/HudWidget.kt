package dev.skylite.ui.hud

import dev.skylite.config.SkyLiteConfig
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * a draggable, scalable overlay element.
 *
 * placement lives in the config as a fraction of the screen rather than pixels,
 * so moving between a laptop screen and an external monitor does not throw
 * everything into a corner.
 */
abstract class HudWidget(
    val id: String,
    val displayName: String,
    private val defaultX: Float = 0.01f,
    private val defaultY: Float = 0.04f
) {

    abstract val width: Int
    abstract val height: Int

    /** whether the module behind this widget is switched on */
    abstract val enabled: Boolean

    /** draws at the origin, the manager has already translated and scaled */
    abstract fun render(g: GuiGraphicsExtractor, editing: Boolean)

    private val placement: SkyLiteConfig.HudPlacement
        get() = SkyLiteConfig.instance.hud.getOrPut(id) {
            SkyLiteConfig.HudPlacement().also {
                it.x = defaultX
                it.y = defaultY
            }
        }

    var scale: Float
        get() = placement.scale
        set(value) {
            placement.scale = value.coerceIn(0.5f, 3.0f)
        }

    fun xFor(screenWidth: Int): Int = (placement.x * screenWidth).toInt()

    fun yFor(screenHeight: Int): Int = (placement.y * screenHeight).toInt()

    /** clamps so a widget can never be dragged fully off screen */
    fun moveTo(px: Int, py: Int, screenWidth: Int, screenHeight: Int) {
        val maxX = (screenWidth - width * scale).coerceAtLeast(1f)
        val maxY = (screenHeight - height * scale).coerceAtLeast(1f)
        placement.x = (px.toFloat() / screenWidth).coerceIn(0f, maxX / screenWidth)
        placement.y = (py.toFloat() / screenHeight).coerceIn(0f, maxY / screenHeight)
    }
}
