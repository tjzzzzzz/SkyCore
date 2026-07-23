package dev.skylite.core.module.general

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.ItemData
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SkillTracker {

    val SKILLS = listOf(
        "Combat",
        "Farming",
        "Fishing",
        "Mining",
        "Foraging",
        "Enchanting",
        "Alchemy",
        "Carpentry",
        "Hunting",
        "Catacombs"
    )

    private val sessions = ConcurrentHashMap<String, SkillSession>()
    private var saveTicks = 0

    fun init() {
        for (skill in SKILLS) {
            sessions.putIfAbsent(skill, SkillSession())
        }

        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!enabled()) return@register
            val plain = ItemData.plain(message)
            if (overlay) {
                onOverlay(plain)
            } else {
                onChat(plain)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled()) return@register
            for (skill in SKILLS) {
                if (isActive(skill)) tickSession(skill)
            }
            saveTicks++
            if (saveTicks >= 1200) {
                if (SKILLS.any { isActive(it) }) SkyLiteConfig.save()
                saveTicks = 0
            }
        }
    }

    fun reset(skill: String) {
        sessions.getOrPut(skill) { SkillSession() }.reset()
        SkyLiteConfig.instance.skillTracker.active[skill] = false
        SkyLiteConfig.save()
    }

    fun isActive(skill: String): Boolean =
        SkyLiteConfig.instance.skillTracker.active[skill] == true

    fun setActive(skill: String, active: Boolean) {
        SkyLiteConfig.instance.skillTracker.active[skill] = active
        if (active) sessions.putIfAbsent(skill, SkillSession())
        SkyLiteConfig.save()
    }

    fun session(skill: String): SkillSession =
        sessions.getOrPut(skill) { SkillSession() }

    fun getDisplayLines(): List<String> {
        val active = SKILLS.filter { isActive(it) }
        if (active.isEmpty()) return listOf("None tracked.")
        val lines = ArrayList<String>()
        for (skill in active) {
            val s = session(skill)
            val paused = isPaused(skill)
            lines += skill + if (paused) " (paused)" else ""
            lines += "EXP/hr: ${formatSep(xpPerHour(s))}"
            lines += "Gained: ${formatSep(s.currentExp)}"
            lines += "Counted: ${ticksToTime(s.countedTicks)}"
            lines += "Elapsed: ${ticksToTime(s.totalTicks)}"
        }
        return lines
    }

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.skillTracker.enabled

    private fun onOverlay(msg: String) {
        val index = msg.indexOf('+')
        if (index < 0) return
        for (skill in SKILLS) {
            if (!msg.contains(skill) || !isActive(skill)) continue
            val close = msg.indexOf(')', index)
            if (close < 0) continue
            val expPart = msg.substring(index, close + 1)
            val obj = session(skill)
            if (obj.lastPart == expPart) continue
            if (!expPart.endsWith("/0)")) {
                val space = expPart.indexOf(' ')
                if (space > 1) addExp(skill, parseExp(expPart.substring(1, space)))
            } else {
                val open = expPart.indexOf('(')
                val slash = expPart.indexOf('/')
                if (open >= 0 && slash > open) {
                    val exp = parseExp(expPart.substring(open + 1, slash))
                    if (exp != 0.0) {
                        if (obj.lastExp != 0.0) addExp(skill, exp - obj.lastExp)
                        obj.lastExp = exp
                    }
                }
            }
            obj.lastPart = expPart
            break
        }
    }

    private fun onChat(msg: String) {
        val trimmed = msg.trim()
        if (trimmed.startsWith("+") && trimmed.endsWith(" Catacombs Experience") && isActive("Catacombs")) {
            val space = trimmed.indexOf(' ')
            if (space > 1) addExp("Catacombs", parseExp(trimmed.substring(1, space)))
        }
    }

    private fun addExp(skill: String, exp: Double) {
        val obj = session(skill)
        obj.currentExp += exp
        obj.pauseTicks = 0
    }

    private fun tickSession(skill: String) {
        val obj = session(skill)
        if (!isPaused(skill)) {
            obj.countedTicks++
            obj.pauseTicks++
        }
        obj.totalTicks++
    }

    private fun isPaused(skill: String): Boolean {
        if (skill == "Catacombs" && LocationManager.current == IslandType.DUNGEONS) return false
        return session(skill).pauseTicks >= 600
    }

    private fun xpPerHour(s: SkillSession): Double {
        if (s.countedTicks <= 0) return 0.0
        return s.currentExp / (s.countedTicks / 72000.0)
    }

    private fun parseExp(raw: String): Double =
        raw.replace(",", "").toDoubleOrNull() ?: 0.0

    private fun formatSep(value: Double): String =
        String.format(Locale.ENGLISH, "%,.1f", value)

    private fun ticksToTime(ticks: Long): String {
        if (ticks < 20) return "0s"
        var current = ticks
        val units = arrayOf("h", "m", "s")
        val durations = intArrayOf(72000, 1200, 20)
        val builder = StringBuilder()
        for (i in 0..2) {
            var amount = 0
            while (current >= durations[i]) {
                amount++
                current -= durations[i]
            }
            if (amount > 0) builder.append(amount).append(units[i])
        }
        return builder.toString().ifEmpty { "0s" }
    }
}
