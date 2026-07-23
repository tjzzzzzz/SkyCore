package dev.skylite.ui.hud

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.module.ToggleSprint
import dev.skylite.ui.render.Fonts
import dev.skylite.ui.render.Ui
import dev.skylite.ui.theme.Theme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * small status pill that shows whether toggle sprint is currently steering the
 * player. matches the info bar language: a dot plus a label in the card.
 */
class SprintWidget : HudWidget("sprint", "Sprint Status", defaultX = 0.01f, defaultY = 0.09f) {

    private companion object {
        val LABEL: Component = Fonts.label("SPRINT", Fonts.SMALL)
        const val HEIGHT = 16
        const val DOT = 4
    }

    private val labelWidth: Int by lazy { Fonts.width(LABEL) }

    override val enabled: Boolean
        get() = SkyLiteConfig.instance.toggleSprint.enabled &&
            SkyLiteConfig.instance.toggleSprint.showHud

    override val width: Int get() = 8 + DOT + 5 + labelWidth + 8
    override val height: Int get() = HEIGHT

    override fun render(g: net.minecraft.client.gui.GuiGraphicsExtractor, editing: Boolean) {
        val on = editing || ToggleSprint.active
        val accent = if (on) Theme.SUCCESS else Theme.TEXT_FAINT

        Ui.panel(g, 0, 0, width, HEIGHT, Theme.SURFACE, Theme.BORDER, 7)
        Ui.roundedRect(g, 8, (HEIGHT - DOT) / 2, DOT, DOT, accent, 2)
        if (on) {
            Ui.roundedRect(g, 7, (HEIGHT - DOT) / 2 - 1, DOT + 2, DOT + 2, Ui.withAlpha(Theme.SUCCESS, 0.30f), 3)
        }
        g.text(Minecraft.getInstance().font, LABEL, 8 + DOT + 5, 5, accent, false)
    }
}
