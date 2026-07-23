package dev.skycore.ui.render

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Ui {

    private val insetCache = HashMap<Int, IntArray>()

    private fun insets(radius: Int): IntArray {
        insetCache[radius]?.let { return it }
        val table = IntArray(radius) { row ->
            val dy = radius - 0.5f - row
            (radius - sqrt(radius * radius - dy * dy)).roundToInt()
        }
        insetCache[radius] = table
        return table
    }

    fun roundedRect(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, radius: Int = 4) {
        if (w <= 0 || h <= 0) return
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color)
            return
        }

        g.fill(x, y + r, x + w, y + h - r, color)

        val table = insets(r)
        for (row in 0 until r) {
            val inset = table[row]
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color)
            g.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, color)
        }
    }

    fun roundedOutline(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, radius: Int = 4) {
        roundedRect(g, x, y, w, h, color, radius)
    }

    fun glow(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, layers: Int = 2) {
        for (i in layers downTo 1) {
            val spread = i * 2
            val alpha = 0.16f / i
            roundedRect(
                g,
                x - spread, y - spread, w + spread * 2, h + spread * 2,
                withAlpha(color, alpha),
                4 + spread
            )
        }
    }

    fun shadow(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, rings: Int = 6) {
        for (i in rings downTo 1) {
            val alpha = 0.055f * (1f - (i - 1f) / rings)
            roundedRect(
                g,
                x - i, y - i + 1, w + i * 2, h + i * 2,
                withAlpha(0xFF000000.toInt(), alpha),
                4 + i
            )
        }
    }

    fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, radius: Int = 4) {
        if (w <= 0 || h <= 0) return
        val r = radius.coerceAtMost(minOf(w, h) / 2)

        if (r <= 0) {
            g.fill(x, y, x + w, y + 1, color)
            g.fill(x, y + h - 1, x + w, y + h, color)
            g.fill(x, y + 1, x + 1, y + h - 1, color)
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color)
            return
        }

        val table = insets(r)

        g.fill(x + r, y, x + w - r, y + 1, color)
        g.fill(x + r, y + h - 1, x + w - r, y + h, color)
        g.fill(x, y + r, x + 1, y + h - r, color)
        g.fill(x + w - 1, y + r, x + w, y + h - r, color)

        for (row in 0 until r) {
            val inset = table[row]
            val prev = if (row == 0) r else table[row - 1]
            val span = (prev - inset).coerceAtLeast(1)

            g.fill(x + inset, y + row, x + inset + span, y + row + 1, color)
            g.fill(x + w - inset - span, y + row, x + w - inset, y + row + 1, color)
            g.fill(x + inset, y + h - 1 - row, x + inset + span, y + h - row, color)
            g.fill(x + w - inset - span, y + h - 1 - row, x + w - inset, y + h - row, color)
        }
    }

    fun panel(
        g: GuiGraphicsExtractor,
        x: Int, y: Int, w: Int, h: Int,
        fill: Int, border: Int, radius: Int = 4
    ) {
        roundedRect(g, x, y, w, h, border, radius)
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, fill, (radius - 1).coerceAtLeast(1))
    }

    fun check(g: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {

        g.fill(x, y + 2, x + 2, y + 4, color)
        g.fill(x + 1, y + 3, x + 3, y + 5, color)
        g.fill(x + 2, y + 4, x + 4, y + 6, color)

        g.fill(x + 3, y + 2, x + 5, y + 4, color)
        g.fill(x + 4, y + 1, x + 6, y + 3, color)
        g.fill(x + 5, y, x + 7, y + 2, color)
    }

    fun cross(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int, thickness: Int = 1) {
        for (i in 0 until size) {
            g.fill(x + i, y + i, x + i + thickness, y + i + thickness, color)
            g.fill(x + size - 1 - i, y + i, x + size - 1 - i + thickness, y + i + thickness, color)
        }
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24 and 0xFF) * alpha).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val a = lerpChannel(from ushr 24 and 0xFF, to ushr 24 and 0xFF, f)
        val r = lerpChannel(from ushr 16 and 0xFF, to ushr 16 and 0xFF, f)
        val g = lerpChannel(from ushr 8 and 0xFF, to ushr 8 and 0xFF, f)
        val b = lerpChannel(from and 0xFF, to and 0xFF, f)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, 255)

    fun approach(current: Float, target: Float, dt: Float, speed: Float = 14f): Float {
        val step = (dt * speed).coerceIn(0f, 1f)
        val next = current + (target - current) * step
        return if (kotlin.math.abs(target - next) < 0.001f) target else next
    }

    fun inBounds(mouseX: Double, mouseY: Double, x: Int, y: Int, w: Int, h: Int): Boolean =
        mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
}
