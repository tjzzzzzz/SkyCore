package dev.skycore.core.skyblock

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

object TabListCache {

    @Volatile
    var area: String = ""
        private set

    @Volatile
    private var lines: List<String> = emptyList()

    @Volatile
    private var dirty = true

    fun init() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            area = ""
            lines = emptyList()
            dirty = true
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (dirty) {
                refresh()
                dirty = false
            }
        }
    }

    fun markDirty() {
        dirty = true
    }

    fun lines(): List<String> {
        if (dirty) {
            refresh()
            dirty = false
        }
        return lines
    }

    fun commissions(): List<String> {
        val all = lines()
        val start = all.indexOfFirst { it.equals("Commissions", ignoreCase = true) || it == "Commissions:" }
        if (start < 0) return emptyList()
        val result = ArrayList<String>()
        for (i in start + 1 until all.size) {
            val line = all[i]
            if (line.isEmpty()) continue
            if (!line.startsWith(" ") && line.contains(":") && !COMMISSION_LINE.matches(line)) break
            val trimmed = line.trim()
            if (COMMISSION_LINE.matches(trimmed)) result.add(trimmed)
        }
        return result
    }

    fun isInArea(name: String): Boolean =
        area.equals(name, ignoreCase = true) || area.contains(name, ignoreCase = true)

    private fun refresh() {
        val connection = Minecraft.getInstance().connection ?: return
        val next = ArrayList<String>()
        var foundArea = area
        for (entry in connection.onlinePlayers) {
            val display = entry.tabListDisplayName ?: continue
            val name = ItemData.plain(display).trim()
            if (name.isEmpty()) continue
            if (name.startsWith("Area: ") || name.startsWith("Dungeon: ")) {
                foundArea = name.substringAfter(":").trim()
            }
            next.add(name)
        }
        area = foundArea
        lines = next
    }

    private val COMMISSION_LINE = Regex(".+:\\s*(?:DONE|\\d+(?:\\.\\d+)?%?)")
}
