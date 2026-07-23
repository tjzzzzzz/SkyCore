package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.ToggleSprint
import dev.skycore.ui.render.Fonts
import dev.skycore.ui.render.Ui
import dev.skycore.ui.theme.Theme
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class SprintWidget : HudWidget("sprint", "Sprint Status", defaultX = 0.01f, defaultY = 0.09f) {

    private companion object {
        val LABEL: Component = Fonts.label("SPRINT", Fonts.SMALL)
        const val HEIGHT = 16
        const val DOT = 4
    }

    private val labelWidth: Int by lazy { Fonts.width(LABEL) }

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.toggleSprint.enabled &&
            SkyCoreConfig.instance.toggleSprint.showHud

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
