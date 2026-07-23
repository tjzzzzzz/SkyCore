package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.dungeon.TerracottaTimer
import dev.skycore.ui.render.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

class TerracottaGyroWidget : HudWidget("terracotta_gyro", "Terracotta Gyro", defaultX = 0.01f, defaultY = 0.44f) {

    private companion object {
        const val PAD_X = 7
        const val PAD_Y = 5
        const val HEIGHT = 16
        const val MIN_W = 88
    }

    private var cachedWidth = MIN_W

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.terracottaTimer.enabled

    override val width: Int get() = cachedWidth
    override val height: Int get() = HEIGHT

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        if (!editing && !TerracottaTimer.isGyroTicking()) return
        val seconds = if (editing && !TerracottaTimer.isGyroTicking()) 13.35f else TerracottaTimer.gyroSeconds()
        val ratio = if (editing && !TerracottaTimer.isGyroTicking()) 1.0 else TerracottaTimer.gyroRatio()
        val color = percentageColor(ratio)
        val label = Fonts.label(String.format("Gyro: %.2fs", seconds), Fonts.SMALL)
        cachedWidth = maxOf(MIN_W, PAD_X * 2 + Fonts.width(label))
        HudStyle.softPanel(g, 0, 0, width, HEIGHT)
        HudStyle.accentBar(g, 0, 2, HEIGHT - 4)
        g.text(Minecraft.getInstance().font, label, PAD_X, PAD_Y, color, false)
    }

    private fun percentageColor(percentage: Double): Int =
        when {
            percentage > 0.66 -> 0xFF55FF55.toInt()
            percentage > 0.33 -> 0xFFFFAA00.toInt()
            else -> 0xFFFF5555.toInt()
        }
}
