package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import net.minecraft.client.gui.GuiGraphicsExtractor

abstract class HudWidget(
    val id: String,
    val displayName: String,
    private val defaultX: Float = 0.01f,
    private val defaultY: Float = 0.04f
) {

    abstract val width: Int
    abstract val height: Int

    abstract val enabled: Boolean

    abstract fun render(g: GuiGraphicsExtractor, editing: Boolean)

    private val placement: SkyCoreConfig.HudPlacement
        get() = SkyCoreConfig.instance.hud.getOrPut(id) {
            SkyCoreConfig.HudPlacement().also {
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

    fun moveTo(px: Int, py: Int, screenWidth: Int, screenHeight: Int) {
        val maxX = (screenWidth - width * scale).coerceAtLeast(1f)
        val maxY = (screenHeight - height * scale).coerceAtLeast(1f)
        placement.x = (px.toFloat() / screenWidth).coerceIn(0f, maxX / screenWidth)
        placement.y = (py.toFloat() / screenHeight).coerceIn(0f, maxY / screenHeight)
    }
}
