package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.ItemData
import dev.skylite.core.skyblock.ScoreboardCache
import dev.skylite.core.skyblock.TabListCache
import dev.skylite.core.skyblock.Titles
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object ShaftAnnounce {

    private const val MESSAGE = "/pc !ptme Entered Mineshaft: {id}. Corpses: {corpses}."

    private var ticks = -1
    private var enteringShaft = false

    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !enabled()) return@register
            val inDwarven = LocationManager.current == IslandType.DWARVEN_MINES || TabListCache.isInArea("Dwarven Mines")
            if (inDwarven && ItemData.plain(message) == "Sending to Mineshaft...") {
                enteringShaft = true
            }
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ticks = if (enteringShaft) 120 else -1
            enteringShaft = false
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled() || !inMineshaft() || ticks < 0) return@register
            if (ticks > 0) {
                ticks--
                return@register
            }
            val corpses = corpseCounts()
            val id = shaftId().ifEmpty { "Unknown ID" }
            val corpseList = if (corpses.isEmpty()) {
                "None"
            } else {
                corpses.entries.joinToString(", ") { "${it.value}x ${it.key}" }
            }
            Titles.sendChatOrCommand(
                MESSAGE
                    .replace("{id}", id)
                    .replace("{corpses}", corpseList)
            )
            ticks = -1
        }
    }

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.shaftAnnounce.enabled

    private fun inMineshaft(): Boolean =
        LocationManager.current == IslandType.GLACITE_MINESHAFTS || TabListCache.isInArea("Mineshaft")

    private fun shaftId(): String {
        for (line in ScoreboardCache.lines()) {
            val token = line.substringAfterLast(' ', "")
            if (token.length == 6 && token[4] == '_') return token
        }
        return ""
    }

    private fun corpseCounts(): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        for (line in TabListCache.lines()) {
            if (line.endsWith(": LOOTED") || line.endsWith(": NOT LOOTED")) {
                val corpse = line.substringBefore(':')
                map[corpse] = (map[corpse] ?: 0) + 1
            }
        }
        return map
    }
}
