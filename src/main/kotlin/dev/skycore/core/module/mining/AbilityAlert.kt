package dev.skycore.core.module.mining

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.location.IslandType
import dev.skycore.core.location.LocationManager
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

object AbilityAlert {

    private const val AVAILABLE_SUFFIX = " is now available!"
    private const val USED_PREFIX = "You used your "
    private const val USED_SUFFIX = " Pickaxe Ability!"

    @Volatile
    private var lastAbility: String = ""

    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !enabled() || LocationManager.current == IslandType.DUNGEONS) return@register
            val plain = ItemData.plain(message).trim()

            if (plain.startsWith(USED_PREFIX) && plain.endsWith(USED_SUFFIX)) {
                lastAbility = plain.removePrefix(USED_PREFIX).removeSuffix(USED_SUFFIX).trim()
                return@register
            }

            if (!plain.endsWith(AVAILABLE_SUFFIX)) return@register
            val name = plain.removeSuffix(AVAILABLE_SUFFIX).trim()
            if (name.isEmpty()) return@register

            val known = name.equals(lastAbility, ignoreCase = true) ||
                name.equals("Mining Speed Boost", ignoreCase = true)
            if (!known) return@register

            lastAbility = name
            Titles.show("§6${name.uppercase()}!", stay = 50, fadeOut = 10)
            Titles.playOrb()
        }
    }

    private fun enabled(): Boolean =
        SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.abilityAlert.enabled
}
