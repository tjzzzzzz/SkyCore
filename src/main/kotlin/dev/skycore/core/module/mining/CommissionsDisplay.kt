package dev.skycore.core.module.mining

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.location.LocationManager
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object CommissionsDisplay {

    data class Commission(val name: String, val progress: Float, val done: Boolean, val label: String)

    @Volatile
    var commissions: List<Commission> = emptyList()
        private set

    private var emptyTicks = 0
    private var warnedMissingWidget = false

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!SkyCoreConfig.instance.enabled || !SkyCoreConfig.instance.commissionsDisplay.enabled) {
                commissions = emptyList()
                emptyTicks = 0
                return@register
            }

            val parsed = TabListCache.commissions().mapNotNull { parse(it) }
            commissions = parsed

            if (parsed.isNotEmpty()) {
                emptyTicks = 0
                return@register
            }

            if (!onMining()) {
                emptyTicks = 0
                return@register
            }

            emptyTicks++
            if (!warnedMissingWidget && emptyTicks >= 100) {
                warnedMissingWidget = true
                Titles.warn("No commissions in tab — enable the Commissions widget under Tab > Widgets.")
            }
        }
    }

    private fun onMining(): Boolean {
        if (LocationManager.current.isMiningIsland) return true
        return TabListCache.isInArea("Dwarven Mines") ||
            TabListCache.isInArea("Crystal Hollows") ||
            TabListCache.isInArea("Glacite") ||
            TabListCache.isInArea("Mineshaft")
    }

    private fun parse(line: String): Commission? {
        val idx = line.lastIndexOf(": ")
        if (idx < 0) return null
        val name = line.substring(0, idx).trim()
        val progressRaw = line.substring(idx + 2).trim()
        if (name.isEmpty()) return null
        if (progressRaw.equals("DONE", ignoreCase = true)) {
            return Commission(name, 100f, true, "DONE")
        }
        val numeric = progressRaw.removeSuffix("%").toFloatOrNull() ?: return null
        return Commission(name, numeric.coerceIn(0f, 100f), false, "${numeric.toInt()}%")
    }
}
