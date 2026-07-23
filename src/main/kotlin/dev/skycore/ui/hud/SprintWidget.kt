package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.ToggleSprint
import dev.skycore.ui.render.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class SprintWidget : HudWidget("sprint", "Sprint Status", defaultX = 0.01f, defaultY = 0.09f) {

    private companion object {
        val LABEL: Component = Fonts.label("SPRINT", Fonts.SMALL)
        const val HEIGHT = 14
        const val PAD_X = 6
    }

    private val labelWidth: Int by lazy { Fonts.width(LABEL) }

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.toggleSprint.enabled &&
            SkyCoreConfig.instance.toggleSprint.showHud

    override val width: Int get() = PAD_X + labelWidth + PAD_X
    override val height: Int get() = HEIGHT

    override fun render(g: net.minecraft.client.gui.GuiGraphicsExtractor, editing: Boolean) {
        val on = editing || ToggleSprint.active
        val color = if (on) HudStyle.GOOD else HudStyle.FAINT

        HudStyle.softPanel(g, 0, 0, width, HEIGHT)
        if (on) HudStyle.accentBar(g, 0, 2, HEIGHT - 4)
        g.text(Minecraft.getInstance().font, LABEL, PAD_X, 3, color, false)
    }
}
