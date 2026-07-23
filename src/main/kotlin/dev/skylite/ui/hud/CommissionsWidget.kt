package dev.skylite.ui.hud

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.LocationManager
import dev.skylite.core.module.mining.CommissionsDisplay
import dev.skylite.ui.render.Fonts
import dev.skylite.ui.render.Ui
import dev.skylite.ui.theme.Theme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class CommissionsWidget : HudWidget("commissions", "Commissions", defaultX = 0.01f, defaultY = 0.18f) {

    private companion object {
        val TITLE: Component = Fonts.label("COMMISSIONS", Fonts.SMALL)
        const val PAD = 10
        const val TITLE_H = 14
        const val ROW_H = 22
        const val BAR_H = 3
        const val MIN_W = 140
        const val PLACEHOLDER_ROWS = 2
    }

    private var cachedWidth = MIN_W
    private var cachedHeight = PAD + TITLE_H + 6 + ROW_H * PLACEHOLDER_ROWS + PAD

    override val enabled: Boolean
        get() = SkyLiteConfig.instance.enabled &&
            SkyLiteConfig.instance.commissionsDisplay.enabled &&
            LocationManager.current.isMiningIsland

    override val width: Int get() = cachedWidth
    override val height: Int get() = cachedHeight

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        val live = CommissionsDisplay.commissions
        val rows = if (live.isNotEmpty()) {
            live
        } else if (editing) {
            listOf(
                CommissionsDisplay.Commission("Mithril Miner", 62f, false, "62%"),
                CommissionsDisplay.Commission("Goblin Slayer", 100f, true, "DONE")
            )
        } else {
            return
        }

        var maxName = Fonts.width(TITLE)
        for (row in rows) {
            val nameW = Fonts.width(Fonts.label(row.name, Fonts.SMALL))
            val labelW = Fonts.width(Fonts.label(row.label, Fonts.SMALL))
            maxName = maxOf(maxName, nameW + 8 + labelW)
        }
        cachedWidth = maxOf(MIN_W, PAD * 2 + maxName)
        cachedHeight = PAD + TITLE_H + 6 + rows.size * ROW_H + PAD - 4

        Ui.shadow(g, 0, 1, width, height, 4)
        Ui.panel(g, 0, 0, width, height, Theme.SURFACE, Theme.BORDER, 8)
        g.fill(PAD, PAD + 11, width - PAD, PAD + 12, Ui.withAlpha(Theme.ACCENT, 0.35f))

        val font = Minecraft.getInstance().font
        g.text(font, TITLE, PAD, PAD, Theme.ACCENT, false)

        var y = PAD + TITLE_H + 6
        for (row in rows) {
            val name = Fonts.label(row.name, Fonts.SMALL)
            val label = Fonts.label(row.label, Fonts.SMALL)
            val accent = if (row.done) Theme.SUCCESS else Theme.ACCENT
            val textColor = if (row.done) Theme.SUCCESS else Theme.TEXT

            g.text(font, name, PAD, y, textColor, false)
            g.text(font, label, width - PAD - Fonts.width(label), y, accent, false)

            val barY = y + 12
            val barW = width - PAD * 2
            Ui.roundedRect(g, PAD, barY, barW, BAR_H, Theme.CONTROL_OFF, 2)
            val fill = ((row.progress / 100f) * barW).roundToInt().coerceIn(0, barW)
            if (fill > 0) {
                Ui.roundedRect(g, PAD, barY, fill, BAR_H, accent, 2)
            }
            y += ROW_H
        }
    }
}
