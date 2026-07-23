package dev.skycore.core.skyblock

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import java.util.Comparator

object TabListCache {

    @Volatile
    var area: String = ""
        private set

    @Volatile
    private var lines: List<String> = emptyList()

    @Volatile
    private var dirty = true
    private var tick = 0

    fun init() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            area = ""
            lines = emptyList()
            dirty = true
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            tick++
            if (dirty || tick % 20 == 0) {
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
        if (all.none { it.trimStart().startsWith("Commissions", ignoreCase = true) }) {
            return emptyList()
        }

        val ordered = ArrayList<String>()
        val seen = HashSet<String>()
        for (raw in all) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("Commissions", ignoreCase = true)) continue
            if (!COMMISSION_LINE.matches(trimmed)) continue
            if (isFalsePositive(trimmed)) continue
            if (seen.add(trimmed)) ordered.add(trimmed)
        }
        return ordered
    }

    fun isInArea(name: String): Boolean =
        area.equals(name, ignoreCase = true) || area.contains(name, ignoreCase = true)

    private fun refresh() {
        val client = Minecraft.getInstance()
        val connection = client.connection ?: return
        val tab = client.gui.hud.tabList

        val source = connection.listedOnlinePlayers.ifEmpty { connection.onlinePlayers }
        val entries: List<PlayerInfo> = source.sortedWith(TAB_ORDER)

        val next = ArrayList<String>(entries.size)
        var foundArea = area
        for (entry in entries) {
            val display = entry.tabListDisplayName ?: continue
            val name = ItemData.plain(display)
            if (name.isBlank()) continue
            val trimmed = name.trim()
            if (trimmed.startsWith("Area: ") || trimmed.startsWith("Dungeon: ")) {
                foundArea = trimmed.substringAfter(":").trim()
            }
            next.add(name)
        }
        area = foundArea
        lines = next
    }

    private fun isFalsePositive(line: String): Boolean {
        val name = line.substringBefore(":").trim()
        if (FALSE_POSITIVE_NAMES.any { it.equals(name, ignoreCase = true) }) return true
        return SKILL_LEVEL_NAME.matches(name)
    }

    private val FALSE_POSITIVE_NAMES = setOf(
        "Area", "Server", "Gems", "Fairy Souls", "Bank", "Interest",
        "Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting",
        "Alchemy", "Carpentry", "Taming", "Social", "Runecrafting", "Hunting"
    )

    private val SKILL_LEVEL_NAME = Regex(
        "^(Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Taming|Social|Runecrafting|Hunting|Catacombs)\\s+\\d+$",
        RegexOption.IGNORE_CASE
    )

    private val COMMISSION_LINE = Regex(".+:\\s*(?:DONE|\\d+(?:\\.\\d+)?%)")

    private val TAB_ORDER: Comparator<PlayerInfo> =
        Comparator.comparingInt(PlayerInfo::getTabListOrder)
            .thenComparing({ it.profile.name }, String.CASE_INSENSITIVE_ORDER)
}
