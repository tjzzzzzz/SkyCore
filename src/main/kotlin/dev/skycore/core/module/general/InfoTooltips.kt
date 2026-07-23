package dev.skycore.core.module.general

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.skyblock.ItemData
import dev.skycore.ui.theme.Theme
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

object InfoTooltips {

    fun init() {}

    private val PREFIX: Component = Component.empty()
        .append(Component.literal("[").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7C8C9E))))
        .append(Component.literal("SkyCore").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Theme.ACCENT and 0xFFFFFF))))
        .append(Component.literal("] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7C8C9E))))

    fun appendLines(stack: ItemStack, lines: MutableList<Component>) {
        if (!enabled() || stack.isEmpty) return
        val cfg = SkyCoreConfig.instance.infoTooltips
        val data = ItemData.customData(stack)

        if (cfg.dungeonQuality) {
            val boost = data.getIntOr("baseStatBoostPercentage", 0)
            val tier = data.getIntOr("item_tier", 0)
            if (boost != 0) {
                val color = if (boost == 50) 0xFF5555 else 0xFFAA00
                lines += line("Quality: $boost/50, Tier $tier", color)
            }
        }

        if (cfg.createdDate) {
            val timestamp = data.getLongOr("timestamp", 0L)
            if (timestamp != 0L) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = timestamp
                val day = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).orEmpty()
                val formatted = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT,
                    Locale.getDefault()
                ).format(calendar.time)
                lines += line("Created: $day $formatted", 0xFFAA00)
            }
        }

        if (cfg.hexColor) {
            val dyed = stack.get(DataComponents.DYED_COLOR)
            if (dyed != null) {
                val hex = String.format(Locale.ROOT, "#%06X", dyed.rgb() and 0xFFFFFF).lowercase(Locale.ROOT)
                lines += line("Dye Color: $hex", 0xFFAA00)
            }
        }

        if (cfg.skyblockId) {
            val id = ItemData.skyblockId(stack)
            if (id.isNotEmpty()) {
                lines += line("Item ID: $id", 0xFFAA00)
            }
        }
    }

    private fun enabled(): Boolean =
        SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.infoTooltips.enabled

    private fun line(text: String, color: Int): Component =
        PREFIX.copy().append(
            Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color and 0xFFFFFF)))
        )
}
