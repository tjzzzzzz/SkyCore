package dev.skycore.ui.render

import dev.skycore.SkyCore
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object Fonts {

    val REGULAR: Style = styleOf("ui")

    val MEDIUM: Style = styleOf("ui_medium")

    val SEMIBOLD: Style = styleOf("ui_semibold")

    val SMALL: Style = styleOf("ui_small")

    private fun styleOf(name: String): Style =
        Style.EMPTY.withFont(FontDescription.Resource(Identifier.fromNamespaceAndPath(SkyCore.MOD_ID, name)))

    fun label(text: String, style: Style): Component =
        Component.literal(text).setStyle(style)

    fun width(text: Component): Int = Minecraft.getInstance().font.width(text)
}
