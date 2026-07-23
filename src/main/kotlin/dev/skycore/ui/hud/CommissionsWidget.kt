package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.mining.CommissionsDisplay
import dev.skycore.ui.render.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class CommissionsWidget : HudWidget("commissions", "Commissions", defaultX = 0.01f, defaultY = 0.18f) {

    private companion object {
        val TITLE: Component = Fonts.label("Commissions", Fonts.SMALL)
        const val PAD_X = 7
        const val PAD_Y = 6
        const val TITLE_H = 11
        const val ROW_H = 16
        const val BAR_H = 2
        const val MIN_W = 132
        const val PLACEHOLDER_ROWS = 2
    }

    private var cachedWidth = MIN_W
    private var cachedHeight = PAD_Y + TITLE_H + 5 + ROW_H * PLACEHOLDER_ROWS + PAD_Y

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.commissionsDisplay.enabled

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
            maxName = maxOf(maxName, nameW + 10 + labelW)
        }
        cachedWidth = maxOf(MIN_W, PAD_X * 2 + maxName)
        cachedHeight = PAD_Y + TITLE_H + 5 + rows.size * ROW_H + PAD_Y - 2

        HudStyle.panel(g, 0, 0, width, height)
        HudStyle.accentBar(g, 0, PAD_Y, TITLE_H)

        val font = Minecraft.getInstance().font
        g.text(font, TITLE, PAD_X + 2, PAD_Y, HudStyle.TITLE, false)
        HudStyle.titleRule(g, PAD_X, PAD_Y + TITLE_H + 1, width - PAD_X * 2)

        var y = PAD_Y + TITLE_H + 5
        for (row in rows) {
            val name = Fonts.label(row.name, Fonts.SMALL)
            val label = Fonts.label(row.label, Fonts.SMALL)
            val valueColor = if (row.done) HudStyle.GOOD else HudStyle.ACCENT
            val textColor = if (row.done) HudStyle.GOOD else HudStyle.TEXT

            g.text(font, name, PAD_X, y, textColor, false)
            g.text(font, label, width - PAD_X - Fonts.width(label), y, valueColor, false)

            HudStyle.meter(
                g,
                PAD_X,
                y + 10,
                width - PAD_X * 2,
                BAR_H,
                row.progress / 100f,
                if (row.done) HudStyle.GOOD else HudStyle.ACCENT
            )
            y += ROW_H
        }
    }
}
