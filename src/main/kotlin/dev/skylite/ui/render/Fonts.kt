package dev.skylite.ui.render

import dev.skylite.SkyLite
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

/**
 * the ui typeface.
 *
 * 26.2 loads truetype natively through the vanilla font pipeline (freetype
 * behind TrueTypeGlyphProviderDefinition), so there is no reason to hand roll a
 * glyph atlas. we ship plus jakarta sans under assets/skylite/font, declare it
 * with a font provider json, and pick it per string with a style. the text still
 * goes through the vanilla batch, which means scissor, matrices and batching all
 * keep working and we add zero draw calls.
 *
 * components are immutable, so every label is built once and cached. nothing
 * here allocates on the render path.
 */
object Fonts {

    /** body copy */
    val REGULAR: Style = styleOf("ui")

    /** buttons, sidebar entries, anything that needs a little more weight */
    val MEDIUM: Style = styleOf("ui_medium")

    /** window title and section headers */
    val SEMIBOLD: Style = styleOf("ui_semibold")

    /** descriptions and badges, a real 8.5px face instead of a scaled bitmap */
    val SMALL: Style = styleOf("ui_small")

    private fun styleOf(name: String): Style =
        Style.EMPTY.withFont(FontDescription.Resource(Identifier.fromNamespaceAndPath(SkyLite.MOD_ID, name)))

    /** builds a cacheable label, call this once and hold the result */
    fun label(text: String, style: Style): Component =
        Component.literal(text).setStyle(style)

    fun width(text: Component): Int = Minecraft.getInstance().font.width(text)
}
