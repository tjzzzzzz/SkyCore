package dev.skylite.ui.hud

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.stats.ServerStats
import dev.skylite.ui.render.Fonts
import dev.skylite.ui.render.Ui
import dev.skylite.ui.theme.Theme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class InfoBarWidget : HudWidget("info_bar", "Info Bar", defaultX = 0.01f, defaultY = 0.04f) {

    private companion object {
        const val HEIGHT = 16
        const val PAD = 8
        const val GAP = 10
        const val DOT = 4
    }

    private var cachedWidth = 80

    override val enabled: Boolean
        get() = SkyLiteConfig.instance.infoBar.enabled

    override val width: Int get() = cachedWidth
    override val height: Int get() = HEIGHT

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        val opts = SkyLiteConfig.instance.infoBar
        val parts = ArrayList<Pair<Component, Int>>(4)

        if (opts.fps) {
            parts += Fonts.label("${ServerStats.fps()} FPS", Fonts.SMALL) to Theme.TEXT
        }
        if (opts.ping) {
            val ping = ServerStats.ping()
            val color = when {
                ping <= 80 -> Theme.SUCCESS
                ping <= 150 -> Theme.WARNING
                else -> Theme.DANGER
            }
            parts += Fonts.label("${ping}ms", Fonts.SMALL) to color
        }
        if (opts.tps) {
            val tps = if (ServerStats.hasTps) ServerStats.tps else 20.0
            val color = when {
                tps >= 19.5 -> Theme.SUCCESS
                tps >= 15.0 -> Theme.WARNING
                else -> Theme.DANGER
            }
            parts += Fonts.label("%.1f TPS".format(tps), Fonts.SMALL) to color
        }
        if (opts.day) {
            parts += Fonts.label("Day ${ServerStats.day()}", Fonts.SMALL) to Theme.TEXT_MUTED
        }

        if (parts.isEmpty() && editing) {
            parts += Fonts.label("INFO", Fonts.SMALL) to Theme.TEXT_MUTED
        }
        if (parts.isEmpty()) return

        var content = 0
        for ((label, _) in parts) content += Fonts.width(label)
        content += GAP * (parts.size - 1).coerceAtLeast(0)
        cachedWidth = PAD + DOT + 5 + content + PAD

        Ui.panel(g, 0, 0, width, HEIGHT, Theme.SURFACE, Theme.BORDER, 7)
        Ui.roundedRect(g, PAD, (HEIGHT - DOT) / 2, DOT, DOT, Theme.ACCENT, 2)
        Ui.roundedRect(g, PAD - 1, (HEIGHT - DOT) / 2 - 1, DOT + 2, DOT + 2, Ui.withAlpha(Theme.ACCENT, 0.25f), 3)

        val font = Minecraft.getInstance().font
        var x = PAD + DOT + 5
        for ((label, color) in parts) {
            g.text(font, label, x, 5, color, false)
            x += Fonts.width(label) + GAP
        }
    }
}
