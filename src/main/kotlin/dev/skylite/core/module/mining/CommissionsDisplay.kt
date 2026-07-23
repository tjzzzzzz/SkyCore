package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.TabListCache
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object CommissionsDisplay {

    data class Commission(val name: String, val progress: Float, val done: Boolean, val label: String)

    @Volatile
    var commissions: List<Commission> = emptyList()
        private set

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!SkyLiteConfig.instance.enabled || !SkyLiteConfig.instance.commissionsDisplay.enabled) {
                commissions = emptyList()
                return@register
            }
            if (!LocationManager.current.isMiningIsland) {
                commissions = emptyList()
                return@register
            }
            commissions = TabListCache.commissions().mapNotNull { parse(it) }
        }
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
