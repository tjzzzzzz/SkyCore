package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.general.SkillTracker
import dev.skycore.ui.render.Fonts
import dev.skycore.ui.render.Ui
import dev.skycore.ui.theme.Theme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class SkillTrackerWidget : HudWidget("skill_tracker", "Skill Tracker", defaultX = 0.01f, defaultY = 0.28f) {

    private companion object {
        val TITLE: Component = Fonts.label("SKILL TRACKER", Fonts.SMALL)
        const val PAD = 10
        const val TITLE_H = 14
        const val ROW_H = 12
        const val MIN_W = 130
    }

    private var cachedWidth = MIN_W
    private var cachedHeight = PAD + TITLE_H + 6 + ROW_H * 2 + PAD

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.skillTracker.enabled

    override val width: Int get() = cachedWidth
    override val height: Int get() = cachedHeight

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        val live = SkillTracker.getDisplayLines()
        val none = live.size == 1 && live[0] == "None tracked."
        val lines = when {
            editing && (live.isEmpty() || none) ->
                listOf("Mining", "EXP/hr: 12,500.0", "Gained: 4,200.0", "Counted: 5m12s", "Elapsed: 6m1s")
            live.isEmpty() || none -> return
            else -> live
        }

        var maxW = Fonts.width(TITLE)
        val labels = lines.map { Fonts.label(it, Fonts.SMALL) }
        for (label in labels) maxW = maxOf(maxW, Fonts.width(label))
        cachedWidth = maxOf(MIN_W, PAD * 2 + maxW)
        cachedHeight = PAD + TITLE_H + 6 + labels.size * ROW_H + PAD - 2

        Ui.shadow(g, 0, 1, width, height, 4)
        Ui.panel(g, 0, 0, width, height, Theme.SURFACE, Theme.BORDER, 8)
        g.fill(PAD, PAD + 11, width - PAD, PAD + 12, Ui.withAlpha(Theme.ACCENT, 0.35f))

        val font = Minecraft.getInstance().font
        g.text(font, TITLE, PAD, PAD, Theme.ACCENT, false)

        var y = PAD + TITLE_H + 6
        for (i in lines.indices) {
            val name = lines[i].substringBefore(" (")
            val color = if (name in SkillTracker.SKILLS) Theme.ACCENT else Theme.TEXT
            g.text(font, labels[i], PAD, y, color, false)
            y += ROW_H
        }
    }
}
