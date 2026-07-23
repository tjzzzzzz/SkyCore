package dev.skycore.ui.hud

import dev.skycore.ui.render.Ui
import net.minecraft.client.gui.GuiGraphicsExtractor

object HudStyle {

    const val SURFACE = 0xB80C0E12.toInt()
    const val SURFACE_SOFT = 0x990A0C10.toInt()
    const val BORDER = 0xFF2A3038.toInt()
    const val RULE = 0xFF2E363F.toInt()
    const val TITLE = 0xFFB7C2CE.toInt()
    const val TEXT = 0xFFE8EEF4.toInt()
    const val MUTED = 0xFF8A96A3.toInt()
    const val FAINT = 0xFF5C6670.toInt()
    const val ACCENT = 0xFF6EB8D6.toInt()
    const val GOOD = 0xFF6BCB8E.toInt()
    const val WARN = 0xFFD6B35A.toInt()
    const val BAD = 0xFFD46A7A.toInt()

    fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        g.fill(x, y, x + w, y + h, SURFACE)
        g.fill(x, y, x + w, y + 1, BORDER)
        g.fill(x, y + h - 1, x + w, y + h, BORDER)
        g.fill(x, y, x + 1, y + h, BORDER)
        g.fill(x + w - 1, y, x + w, y + h, BORDER)
    }

    fun softPanel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        g.fill(x, y, x + w, y + h, SURFACE_SOFT)
    }

    fun titleRule(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int) {
        g.fill(x, y, x + w, y + 1, RULE)
    }

    fun accentBar(g: GuiGraphicsExtractor, x: Int, y: Int, h: Int) {
        g.fill(x, y, x + 1, y + h, ACCENT)
    }

    fun meter(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, progress: Float, fill: Int) {
        g.fill(x, y, x + w, y + h, Ui.withAlpha(BORDER, 0.85f))
        val filled = ((progress.coerceIn(0f, 1f)) * w).toInt().coerceIn(0, w)
        if (filled > 0) g.fill(x, y, x + filled, y + h, fill)
    }
}
