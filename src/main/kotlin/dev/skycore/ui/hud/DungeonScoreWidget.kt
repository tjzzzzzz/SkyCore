package dev.skycore.ui.hud

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.module.dungeon.ScoreCalculator
import dev.skycore.ui.render.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

class DungeonScoreWidget : HudWidget("dungeon_score", "Dungeon Score", defaultX = 0.01f, defaultY = 0.38f) {

    private companion object {
        const val PAD_X = 7
        const val PAD_Y = 5
        const val HEIGHT = 16
        const val MIN_W = 96
    }

    private var cachedWidth = MIN_W

    override val enabled: Boolean
        get() = SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.scoreCalculator.enabled

    override val width: Int get() = cachedWidth
    override val height: Int get() = HEIGHT

    override fun render(g: GuiGraphicsExtractor, editing: Boolean) {
        if (!editing && (!DungeonUtil.inDungeons() || !DungeonUtil.dungeonStarted())) return
        val score = ScoreCalculator.getScore()
        val text = if (score > 0) {
            val label = scoreLabel(score)
            val prefix = if (score >= 300) "§l" else ""
            "Score: $prefix$score§r §7($label)"
        } else {
            "Score: §fN/A"
        }
        val color = if (score > 0) scoreColor(score) else HudStyle.TEXT
        val label = Fonts.label(text, Fonts.SMALL)
        cachedWidth = maxOf(MIN_W, PAD_X * 2 + Fonts.width(label))
        HudStyle.softPanel(g, 0, 0, width, HEIGHT)
        HudStyle.accentBar(g, 0, 2, HEIGHT - 4)
        g.text(Minecraft.getInstance().font, label, PAD_X, PAD_Y, color, false)
    }

    private fun scoreColor(score: Int): Int =
        when {
            score < 100 -> 0xFFFC3E1C.toInt()
            score < 160 -> 0xFF35A0FD.toInt()
            score < 230 -> 0xFF90F35D.toInt()
            score < 270 -> 0xFFBE1AFF.toInt()
            else -> 0xFFFFCE1A.toInt()
        }

    private fun scoreLabel(score: Int): String =
        when {
            score < 100 -> "D"
            score < 160 -> "C"
            score < 230 -> "B"
            score < 270 -> "A"
            score < 300 -> "S"
            else -> "S+"
        }
}
