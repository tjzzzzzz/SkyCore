package dev.skycore.ui.render

import net.minecraft.client.gui.GuiGraphicsExtractor

object Icons {

    fun sliders(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {

        g.fill(x, y + 1, x + 10, y + 2, color)
        g.fill(x, y + 5, x + 10, y + 6, color)
        g.fill(x, y + 9, x + 10, y + 10, color)
        g.fill(x + 6, y, x + 8, y + 3, color)
        g.fill(x + 2, y + 4, x + 4, y + 7, color)
        g.fill(x + 7, y + 8, x + 9, y + 11, color)
    }

    fun castle(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {

        g.fill(x, y + 1, x + 2, y + 4, color)
        g.fill(x + 4, y, x + 6, y + 4, color)
        g.fill(x + 8, y + 1, x + 10, y + 4, color)
        g.fill(x, y + 4, x + 10, y + 10, color)
    }

    fun coin(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        Ui.roundedRect(g, x, y, 10, 10, color, 5)
        Ui.roundedRect(g, x + 3, y + 2, 4, 6, 0xFF000000.toInt(), 2)
        g.fill(x + 4, y + 3, x + 6, y + 7, color)
    }

    fun pin(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {

        g.fill(x + 3, y, x + 7, y + 1, color)
        g.fill(x + 2, y + 1, x + 8, y + 5, color)
        g.fill(x + 3, y + 5, x + 7, y + 7, color)
        g.fill(x + 4, y + 7, x + 6, y + 9, color)
        g.fill(x + 4, y + 2, x + 6, y + 4, 0xFF000000.toInt())
    }

    fun brand(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int, dim: Int) {
        g.fill(x, y + 3, x + 3, y + 5, color)
        g.fill(x + 3, y + 1, x + 6, y + 3, color)
        g.fill(x + 6, y + 3, x + 9, y + 5, color)
        g.fill(x, y + 7, x + 3, y + 9, dim)
        g.fill(x + 3, y + 5, x + 6, y + 7, dim)
        g.fill(x + 6, y + 7, x + 9, y + 9, dim)
    }

    fun chevron(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int, expanded: Boolean) {
        if (expanded) {
            g.fill(x, y + 1, x + 7, y + 2, color)
            g.fill(x + 1, y + 2, x + 6, y + 3, color)
            g.fill(x + 2, y + 3, x + 5, y + 4, color)
            g.fill(x + 3, y + 4, x + 4, y + 5, color)
        } else {
            g.fill(x + 1, y, x + 2, y + 7, color)
            g.fill(x + 2, y + 1, x + 3, y + 6, color)
            g.fill(x + 3, y + 2, x + 4, y + 5, color)
            g.fill(x + 4, y + 3, x + 5, y + 4, color)
        }
    }

    fun layout(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        g.fill(x, y, x + 4, y + 4, color)
        g.fill(x + 5, y, x + 9, y + 2, color)
        g.fill(x, y + 5, x + 4, y + 9, color)
        g.fill(x + 5, y + 3, x + 9, y + 9, color)
    }

    fun magnifier(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        Ui.frame(g, x, y, 7, 7, color, 3)
        g.fill(x + 6, y + 6, x + 9, y + 9, color)
    }

    fun eye(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        g.fill(x + 1, y + 3, x + 9, y + 4, color)
        g.fill(x, y + 4, x + 10, y + 7, color)
        g.fill(x + 1, y + 7, x + 9, y + 8, color)
        g.fill(x + 3, y + 4, x + 7, y + 7, 0xFF000000.toInt())
        g.fill(x + 4, y + 5, x + 6, y + 6, color)
    }

    fun pickaxe(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        g.fill(x + 5, y, x + 10, y + 2, color)
        g.fill(x + 7, y + 2, x + 10, y + 5, color)
        g.fill(x + 3, y + 4, x + 6, y + 7, color)
        g.fill(x + 1, y + 6, x + 4, y + 9, color)
        g.fill(x, y + 8, x + 2, y + 10, color)
    }

    fun reset(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        g.fill(x + 2, y, x + 8, y + 1, color)
        g.fill(x + 8, y + 1, x + 9, y + 6, color)
        g.fill(x + 2, y + 6, x + 8, y + 7, color)
        g.fill(x + 1, y + 2, x + 2, y + 5, color)
        g.fill(x + 6, y + 0, x + 9, y + 2, color)
        g.fill(x + 7, y - 1, x + 8, y + 1, color)
    }
}
