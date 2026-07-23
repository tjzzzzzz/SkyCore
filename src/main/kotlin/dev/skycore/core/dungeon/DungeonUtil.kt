package dev.skycore.core.dungeon

import dev.skycore.core.location.IslandType
import dev.skycore.core.location.LocationManager
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.ScoreboardCache
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.mixin.client.PlayerTabOverlayAccessor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap

object DungeonUtil {

    data class Teammate(val name: String, val selectedClass: String)

    private val classCache = ConcurrentHashMap<String, String>()
    private val dungeonClasses = setOf("Healer", "Mage", "Berserk", "Archer", "Tank")
    private val chestNames = setOf("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock")
    private val mapId = MapId(1024)

    @Volatile
    private var teammateEntities: List<AbstractClientPlayer> = emptyList()

    @Volatile
    var currentFloor: String = ""
        private set

    @Volatile
    private var partyCount = 0

    @Volatile
    var powerLevel: Double = 0.0
        private set

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    fun reset() {
        classCache.clear()
        teammateEntities = emptyList()
        currentFloor = ""
        partyCount = 0
        powerLevel = 0.0
    }

    fun inDungeons(): Boolean =
        LocationManager.current == IslandType.DUNGEONS || TabListCache.isInArea("Catacombs")

    fun classes(): Map<String, String> = classCache

    fun chestNames(): Set<String> = chestNames

    fun aliveTeammates(excludeSelf: Boolean = false): List<Teammate> {
        val playerName = Minecraft.getInstance().player?.gameProfile?.name.orEmpty()
        val list = ArrayList<Teammate>()
        for (line in TabListCache.lines()) {
            if (!line.endsWith(")")) continue
            for (dungeonClass in dungeonClasses) {
                if (!line.contains("($dungeonClass")) continue
                val start = line.lastIndexOf(']') + 2
                if (start < 2 || start >= line.length) continue
                val space = line.indexOf(' ', start)
                if (space < 0) continue
                val name = line.substring(start, space)
                if (excludeSelf && name.equals(playerName, ignoreCase = true)) break
                list += Teammate(name, dungeonClass)
                break
            }
        }
        return list
    }

    fun dungeonStarted(): Boolean = Minecraft.getInstance().level?.getMapData(mapId) != null

    fun onFloor(floor: String): Boolean = currentFloor.endsWith(floor)

    fun playerClass(name: String): String = classCache[name].orEmpty()

    fun playerClass(): String {
        val name = Minecraft.getInstance().player?.gameProfile?.name ?: return ""
        return playerClass(name)
    }

    fun isClass(dungeonClass: String): Boolean =
        playerClass().equals(dungeonClass, ignoreCase = true)

    fun teammateEntities(): List<AbstractClientPlayer> = teammateEntities

    fun inDragonPhase(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return player.y < 50.0 && onFloor("7") && inBossRoom()
    }

    fun inBossRoom(floor: String = currentFloor): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (!onFloor(floor)) return false
        val digit = if (floor.length == 2) floor.substring(1, 2) else floor
        val pos = player.position()
        return bossBox(digit)?.contains(pos) == true
    }

    fun isSecretBat(entity: Entity): Boolean {
        if (entity !is Bat) return false
        return isBaseHealth(entity, 100f) && !inBossRoom("4")
    }

    fun isRealPlayer(entity: Entity): Boolean =
        entity is AbstractClientPlayer && entity.uuid.version() == 4

    fun isMob(entity: Entity): Boolean {
        if (entity is AbstractClientPlayer) return !isRealPlayer(entity) && entity.isAlive
        return entity is LivingEntity && entity.isAlive
    }

    fun isBaseHealth(entity: LivingEntity, health: Float): Boolean {
        val current = entity.health
        val difference = current - health
        return current >= health && (current % health == 0f || (current - difference) % health == 0f)
    }

    fun findNametagOwner(armorStand: Entity, others: Collection<Entity>): Entity? {
        var best: Entity? = null
        var lowest = 2.0
        val maxY = armorStand.y
        val ax = armorStand.x
        val az = armorStand.z
        for (ent in others) {
            if (ent is net.minecraft.world.entity.decoration.ArmorStand) continue
            if (ent.y >= maxY) continue
            val dx = ent.x - ax
            val dz = ent.z - az
            val dist = kotlin.math.sqrt(dx * dx + dz * dz)
            if (dist < lowest) {
                best = ent
                lowest = dist
            }
        }
        return best
    }

    fun nearby(from: Entity, dx: Double, dy: Double, dz: Double, filter: (Entity) -> Boolean): List<Entity> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        val box = from.boundingBox.inflate(dx, dy, dz)
        val out = ArrayList<Entity>()
        for (ent in level.entitiesForRendering()) {
            if (ent === from) continue
            if (!filter(ent)) continue
            if (!ent.boundingBox.intersects(box)) continue
            out += ent
        }
        return out
    }

    fun findGround(pos: net.minecraft.core.BlockPos, maxDist: Int): net.minecraft.core.BlockPos {
        val level = Minecraft.getInstance().level ?: return pos
        val dist = maxDist.coerceIn(0, 256)
        for (i in 0..dist) {
            val below = pos.below(i)
            if (!level.getBlockState(below).isAir) return below
        }
        return pos
    }

    fun parseRoman(roman: String): Int {
        var result = 0
        for (i in roman.indices) {
            val number = romanToInt(roman[i])
            if (number == 0) return 0
            if (i != roman.lastIndex) {
                val next = romanToInt(roman[i + 1])
                if (number < next) result -= number else result += number
            } else {
                result += number
            }
        }
        return result
    }

    fun footerLines(): List<String> {
        val footer = (Minecraft.getInstance().gui.hud.tabList as? PlayerTabOverlayAccessor)?.footer
            ?: return emptyList()
        return footer.string.split('\n').map { it.trim().replace(FORMATTING, "") }.filter { it.isNotEmpty() }
    }

    fun locationLine(): String {
        for (line in ScoreboardCache.lines()) {
            if (line.contains("The Catacombs (")) return line
        }
        return ""
    }

    private val FORMATTING = Regex("(?i)§[0-9A-FK-OR]")

    private fun tick() {
        if (!inDungeons()) return
        if (currentFloor.isEmpty()) {
            val location = locationLine()
            val start = location.indexOf('(')
            val end = location.indexOf(')')
            if (start >= 0 && end > start) {
                currentFloor = location.substring(start + 1, end)
            }
        }
        if ((partyCount == 0 || classCache.size != partyCount) && dungeonStarted()) {
            for (line in TabListCache.lines()) {
                if (line.startsWith("Party (") && line.endsWith(")")) {
                    partyCount = line.substringAfter('(').removeSuffix(")").toIntOrNull() ?: 0
                    break
                }
            }
            for (teammate in aliveTeammates()) {
                classCache[teammate.name] = teammate.selectedClass
            }
        }
        var power = 0.0
        for (line in footerLines()) {
            if (line.startsWith("Blessing of Power")) {
                power += parseRoman(line.removePrefix("Blessing of Power").trim())
            }
            if (line.startsWith("Blessing of Time")) {
                power += 0.5 * parseRoman(line.removePrefix("Blessing of Time").trim())
            }
        }
        powerLevel = power
        val teammates = ArrayList<AbstractClientPlayer>()
        val level = Minecraft.getInstance().level
        if (level != null) {
            for (player in level.players()) {
                if (player is AbstractClientPlayer && isRealPlayer(player) && playerClass(player.gameProfile.name).isNotEmpty()) {
                    teammates += player
                }
            }
        }
        teammateEntities = teammates
    }

    private fun bossBox(digit: String): AABB? = when (digit) {
        "1" -> AABB(-72.0, 146.0, -40.0, -14.0, 55.0, 49.0)
        "2" -> AABB(-40.0, 99.0, -40.0, 24.0, 54.0, 54.0)
        "3" -> AABB(-40.0, 118.0, -40.0, 42.0, 64.0, 73.0)
        "4" -> AABB(50.0, 112.0, 81.0, -40.0, 53.0, -40.0)
        "5" -> AABB(50.0, 112.0, 118.0, -40.0, 53.0, -8.0)
        "6" -> AABB(22.0, 110.0, 134.0, -40.0, 51.0, -8.0)
        "7" -> AABB(134.0, 254.0, 147.0, -8.0, 0.0, -8.0)
        else -> null
    }

    private fun romanToInt(c: Char): Int = when (c.uppercaseChar()) {
        'I' -> 1
        'V' -> 5
        'X' -> 10
        'L' -> 50
        'C' -> 100
        'D' -> 500
        'M' -> 1000
        else -> 0
    }
}
