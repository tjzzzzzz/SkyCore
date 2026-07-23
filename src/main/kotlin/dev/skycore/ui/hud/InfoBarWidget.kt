package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.stats.ServerStats
import dev.skycore.ui.render.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class InfoBarWidget : HudWidget("info_bar", "Info Bar", defaultX = 0.01f, defaultY = 0.04f) {

    private companion object {
        const val HEIGHT = 14
        const val PAD_X = 6
        const val GAP = 8
    }

    private var cachedWidth = 80

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.infoBar.enabled

    override val width: Int get() = cachedWidth
    override val height: Int get() = HEIGHT

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        val opts = SkyCoreConfig.instance.infoBar
        val parts = ArrayList<Pair<Component, Int>>(4)

        if (opts.fps) {
            val fps = ServerStats.fps()
            val color = when {
                fps >= 60 -> HudStyle.GOOD
                fps >= 30 -> HudStyle.WARN
                else -> HudStyle.BAD
            }
            parts += Fonts.label("$fps FPS", Fonts.SMALL) to color
        }
        if (opts.ping) {
            val ping = ServerStats.ping()
            val color = when {
                ping <= 80 -> HudStyle.GOOD
                ping <= 150 -> HudStyle.WARN
                else -> HudStyle.BAD
            }
            parts += Fonts.label("${ping}ms", Fonts.SMALL) to color
        }
        if (opts.tps) {
            val tps = if (ServerStats.hasTps) ServerStats.tps else 20.0
            val color = when {
                tps >= 19.5 -> HudStyle.GOOD
                tps >= 15.0 -> HudStyle.WARN
                else -> HudStyle.BAD
            }
            parts += Fonts.label("%.1f TPS".format(tps), Fonts.SMALL) to color
        }
        if (opts.day) {
            parts += Fonts.label("Day ${ServerStats.day()}", Fonts.SMALL) to HudStyle.MUTED
        }

        if (parts.isEmpty() && editing) {
            parts += Fonts.label("INFO", Fonts.SMALL) to HudStyle.MUTED
        }
        if (parts.isEmpty()) return

        var content = 0
        for ((label, _) in parts) content += Fonts.width(label)
        content += GAP * (parts.size - 1).coerceAtLeast(0)
        cachedWidth = PAD_X + content + PAD_X

        HudStyle.softPanel(g, 0, 0, width, HEIGHT)

        val font = Minecraft.getInstance().font
        var x = PAD_X
        for ((label, color) in parts) {
            g.text(font, label, x, 3, color, false)
            x += Fonts.width(label) + GAP
        }
    }
}
