package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.ItemData
import dev.skylite.core.skyblock.TabListCache
import dev.skylite.core.skyblock.Titles
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.world.item.ItemStack

object AbilityAlert {

    private var tool: ItemStack = ItemStack.EMPTY
    private var ability: String = ""
    private var ticks = 0

    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !enabled() || LocationManager.current == IslandType.DUNGEONS) return@register
            val plain = ItemData.plain(message)
            if (!plain.startsWith("You used your ") || !plain.endsWith(" Pickaxe Ability!")) return@register
            if (tool.isEmpty || ability.isEmpty() || widgetLine().isNotEmpty()) return@register
            val override = SkyLiteConfig.instance.abilityAlert.overrideTicks
            if (override > 0) {
                ticks = override
            } else {
                ItemData.cooldownSeconds(tool)?.let { ticks = it * 20 }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled() || LocationManager.current == IslandType.DUNGEONS || tool.isEmpty || ability.isEmpty()) return@register
            val widget = widgetLine()
            if (widget.isNotEmpty()) {
                val duration = widget.substringAfter(": ").trim()
                if (ticks > 1 && duration == "Available") ticks = 1
                if (ticks == 0 && duration.endsWith("s")) {
                    val seconds = duration.removeSuffix("s").toIntOrNull()
                    if (seconds != null && seconds >= 10) ticks = seconds * 20 + 20
                }
            }
            if (ticks > 0) {
                ticks--
                if (ticks == 0) {
                    Titles.show("§6${ability.uppercase()}!", stay = 50, fadeOut = 10)
                    Titles.playOrb()
                }
            }
        }
    }

    fun onInventory(stack: ItemStack) {
        if (!enabled()) return
        if (ItemData.isMiningTool(stack)) {
            tool = stack.copy()
            ability = ItemData.abilityName(stack)
        }
    }

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.abilityAlert.enabled

    private fun widgetLine(): String {
        val lines = TabListCache.lines()
        for (i in lines.indices) {
            if (lines[i] == "Pickaxe Ability:" && i + 1 < lines.size) {
                val next = lines[i + 1]
                if (next.contains(": ") && (next.endsWith("s") || next.endsWith("Available"))) {
                    return next
                }
            }
        }
        return ""
    }
}
