package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.module.general.SkillTracker
import dev.skycore.ui.render.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class SkillTrackerWidget : HudWidget("skill_tracker", "Skill Tracker", defaultX = 0.01f, defaultY = 0.28f) {

    private companion object {
        val TITLE: Component = Fonts.label("Skill Tracker", Fonts.SMALL)
        const val PAD_X = 7
        const val PAD_Y = 6
        const val TITLE_H = 11
        const val ROW_H = 11
        const val MIN_W = 120
    }

    private var cachedWidth = MIN_W
    private var cachedHeight = PAD_Y + TITLE_H + 5 + ROW_H * 2 + PAD_Y

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.skillTracker.enabled

    override val width: Int get() = cachedWidth
    override val height: Int get() = cachedHeight

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        val live = SkillTracker.getDisplayLines()
        val lines = when {
            editing && live.isEmpty() ->
                listOf("Mining", "EXP/hr: 12,500.0", "Gained: 4,200.0", "Counted: 5m12s", "Elapsed: 6m1s")
            live.isEmpty() -> return
            else -> live
        }

        var maxW = Fonts.width(TITLE)
        val labels = lines.map { Fonts.label(it, Fonts.SMALL) }
        for (label in labels) maxW = maxOf(maxW, Fonts.width(label))
        cachedWidth = maxOf(MIN_W, PAD_X * 2 + maxW)
        cachedHeight = PAD_Y + TITLE_H + 5 + labels.size * ROW_H + PAD_Y - 1

        HudStyle.panel(g, 0, 0, width, height)
        HudStyle.accentBar(g, 0, PAD_Y, TITLE_H)

        val font = Minecraft.getInstance().font
        g.text(font, TITLE, PAD_X + 2, PAD_Y, HudStyle.TITLE, false)
        HudStyle.titleRule(g, PAD_X, PAD_Y + TITLE_H + 1, width - PAD_X * 2)

        var y = PAD_Y + TITLE_H + 5
        for (i in lines.indices) {
            val name = lines[i].substringBefore(" (")
            val color = if (name in SkillTracker.SKILLS) HudStyle.ACCENT else HudStyle.TEXT
            g.text(font, labels[i], PAD_X, y, color, false)
            y += ROW_H
        }
    }
}
