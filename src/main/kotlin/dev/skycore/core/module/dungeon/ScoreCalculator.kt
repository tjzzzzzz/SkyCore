package dev.skycore.core.module.dungeon

import dev.skycore.SkyCore
import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.PartyChat
import dev.skycore.core.skyblock.ScoreboardCache
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.core.skyblock.Titles
import dev.skycore.net.SkyCoreHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import kotlin.math.floor
import kotlin.math.round

object ScoreCalculator {

    private const val ELECTION_URL = "https://whatyouth.ing/api/nofrills/v1/election/get-active-perks"
    private const val BLOOD_DONE = "[BOSS] The Watcher: You have proven yourself. You may pass."

    private val scoreKeywords = listOf("kill", "dead", "score", "smoke")

    @Volatile
    private var score = 0

    @Volatile
    private var bloodDone = false

    @Volatile
    private var mimic = false

    @Volatile
    private var prince = false

    @Volatile
    private var sent270 = false

    @Volatile
    private var sent300 = false

    @Volatile
    private var electionPerks: Set<String> = emptySet()

    @Volatile
    private var electionTick = 0

    @Volatile
    private var fetchingElection = false

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            score = 0
            bloodDone = false
            mimic = false
            prince = false
            sent270 = false
            sent300 = false
            electionTick = 0
        }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !active() || !DungeonUtil.inDungeons()) return@register
            if (ItemData.plain(message).trim() == BLOOD_DONE) bloodDone = true
        }
        PartyChat.onMessage { msg ->
            if (!active() || !DungeonUtil.inDungeons()) return@onMessage
            val lower = msg.content.lowercase()
            if (scoreKeywords.none { lower.contains(it) }) return@onMessage
            if (lower.contains("mimic")) {
                if (!DungeonUtil.onFloor("6") && !DungeonUtil.onFloor("7")) return@onMessage
                setMimicKilled()
            } else if (lower.contains("prince")) {
                setPrinceKilled()
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!active()) return@register
            tickElection()
            if (!DungeonUtil.inDungeons() || !DungeonUtil.dungeonStarted()) return@register
            if (clearedPercent() == 0.0) return@register
            val totalRooms = totalRooms()
            val clearedRooms = totalClearedRooms()
            val secretsFound = secretsFound()
            val secretsNeeded = secretsNeeded()
            score = skillScore(clearedRooms.toDouble(), totalRooms.toDouble()) +
                exploreScore(clearedRooms.toDouble(), totalRooms.toDouble(), secretsFound, secretsNeeded) +
                speedScore() +
                bonusScore()
            if (score >= 300 && !sent300) {
                processAlert(
                    SkyCoreConfig.instance.scoreCalculator.sendMsg300,
                    SkyCoreConfig.instance.scoreCalculator.msg300,
                    SkyCoreConfig.instance.scoreCalculator.showTitle300,
                    SkyCoreConfig.instance.scoreCalculator.title300
                )
                sent300 = true
                sent270 = true
            }
            if (score >= 270 && !sent270) {
                processAlert(
                    SkyCoreConfig.instance.scoreCalculator.sendMsg270,
                    SkyCoreConfig.instance.scoreCalculator.msg270,
                    SkyCoreConfig.instance.scoreCalculator.showTitle270,
                    SkyCoreConfig.instance.scoreCalculator.title270
                )
                sent270 = true
            }
        }
    }

    fun setMimicKilled() {
        mimic = true
    }

    fun setPrinceKilled() {
        prince = true
    }

    fun getScore(): Int = score

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.scoreCalculator.enabled

    private fun processAlert(send: Boolean, msg: String, doTitle: Boolean, title: String) {
        if (send) Titles.sendChatOrCommand(msg)
        if (doTitle) {
            Titles.show(title.replace('&', '§'), fadeIn = 0, stay = 30, fadeOut = 10)
            Titles.playOrb()
        }
    }

    private fun isEzpz(): Boolean =
        when (SkyCoreConfig.instance.scoreCalculator.paulState.lowercase()) {
            "active" -> true
            "inactive" -> false
            else -> electionPerks.contains("EZPZ")
        }

    private fun tickElection() {
        if (!SkyCoreConfig.instance.scoreCalculator.paulState.equals("Auto", ignoreCase = true)) return
        electionTick++
        if (electionTick % 2400 != 0 || fetchingElection) return
        fetchingElection = true
        SkyCore.scope.launch {
            try {
                val text = SkyCoreHttp.instance.get(ELECTION_URL).bodyAsText()
                val root = SkyCoreHttp.json.parseToJsonElement(text).jsonObject
                electionPerks = root["perks"]?.jsonArray
                    ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                    ?.toSet()
                    ?: emptySet()
            } catch (_: Exception) {
            } finally {
                fetchingElection = false
            }
        }
    }

    private fun lineValue(line: String): String {
        var value = line
        if (value.contains('%')) value = value.substring(0, value.indexOf('%'))
        return value.substringAfter(':').trim()
    }

    private fun floored(value: Double): Int = floor(value).toInt()

    private fun parseTime(line: String, unit: String): Int {
        val index = line.indexOf(unit)
        if (index == -1) return 0
        val start = line.lastIndexOf(' ', index).coerceAtLeast(0)
        var time = line.substring(start, index).trim()
        if (time.startsWith('0') && time.length > 1) time = time.substring(1)
        return time.toIntOrNull() ?: 0
    }

    private fun clearedPercent(): Double {
        for (line in ScoreboardCache.lines()) {
            if (!line.startsWith("Cleared: ")) continue
            return (lineValue(line).toDoubleOrNull() ?: 0.0) * 0.01
        }
        return 0.0
    }

    private fun secretsFound(): Double {
        for (line in TabListCache.lines()) {
            if (line.startsWith("Secrets Found: ") && line.endsWith("%")) {
                return (lineValue(line).toDoubleOrNull() ?: 0.0) * 0.01
            }
        }
        return 0.0
    }

    private fun clearedRooms(): Int {
        for (line in TabListCache.lines()) {
            if (line.startsWith("Completed Rooms: ")) {
                return lineValue(line).toIntOrNull() ?: 0
            }
        }
        return 0
    }

    private fun secondsElapsed(): Int {
        for (line in TabListCache.lines().asReversed()) {
            if (!line.startsWith("Time: ")) continue
            val time = line.substringAfter(':')
            return parseTime(time, "m") * 60 + parseTime(time, "s")
        }
        return 0
    }

    private fun totalClearedRooms(): Int {
        var rooms = clearedRooms()
        if (!bloodDone) rooms += 1
        if (!DungeonUtil.inBossRoom()) rooms += 1
        return rooms
    }

    private fun secretsNeeded(): Double =
        when (DungeonUtil.currentFloor) {
            "F1" -> 0.3
            "F2" -> 0.4
            "F3" -> 0.5
            "F4" -> 0.6
            "F5" -> 0.7
            "F6" -> 0.85
            else -> 1.0
        }

    private fun timeLimit(): Int =
        when (DungeonUtil.currentFloor) {
            "F1", "F2", "F3", "F5", "M6" -> 600
            "F4", "F6" -> 720
            "F7", "M7" -> 840
            "M1", "M2", "M3", "M4", "M5" -> 480
            else -> 0
        }

    private fun totalRooms(): Int {
        val percent = clearedPercent()
        if (percent == 0.0) return 0
        return round(clearedRooms() / percent).toInt()
    }

    private fun deathPenalty(): Int {
        for (line in TabListCache.lines()) {
            if (line.startsWith("Team Deaths: ")) {
                return ((lineValue(line).toIntOrNull() ?: 0) * 2 - 1).coerceAtLeast(0)
            }
        }
        return 0
    }

    private fun puzzlePenalty(): Int {
        var failed = 0
        for (line in TabListCache.lines()) {
            if (line.contains(": [✖]") || line.contains(": [✦]")) failed += 1
        }
        return 10 * failed
    }

    private fun skillScore(clearedRooms: Double, totalRooms: Double): Int {
        if (totalRooms == 0.0) return 20
        return 20 + (floored(80.0 * clearedRooms / totalRooms).coerceAtMost(80) - puzzlePenalty() - deathPenalty())
            .coerceIn(0, 80)
    }

    private fun exploreScore(
        clearedRooms: Double,
        totalRooms: Double,
        secretsFound: Double,
        secretsNeeded: Double
    ): Int {
        if (totalRooms == 0.0 || secretsNeeded == 0.0) return 0
        return (
            floored(60.0 * clearedRooms / totalRooms).coerceAtMost(60) +
                floored(40.0 * secretsFound / secretsNeeded).coerceAtMost(40)
            ).coerceIn(0, 100)
    }

    private fun speedScore(): Int {
        val overtime = secondsElapsed() + 480 - timeLimit()
        return when {
            overtime < 492 -> 100
            overtime < 600 -> floored(140 - overtime / 12.0)
            overtime < 840 -> floored(115 - overtime / 24.0)
            overtime < 1140 -> floored(108 - overtime / 30.0)
            overtime < 3570 -> floored(98.5 - overtime / 40.0)
            else -> 0
        }
    }

    private fun bonusScore(): Int {
        var bonus = 0
        if (mimic) bonus += 2
        if (prince) bonus += 1
        if (isEzpz()) bonus += 10
        for (line in TabListCache.lines()) {
            if (line.startsWith("Crypts: ")) {
                bonus += (lineValue(line).toIntOrNull() ?: 0).coerceIn(0, 5)
            }
        }
        return bonus
    }
}
