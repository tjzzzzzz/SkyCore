package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.render.WorldLabels
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object WitherDragons {

    private val dragons = listOf(
        Dragon.RED,
        Dragon.ORANGE,
        Dragon.BLUE,
        Dragon.PURPLE,
        Dragon.GREEN
    )

    @Volatile
    private var splitDone = false

    @Volatile
    private var tickCounter = 0

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            splitDone = false
            tickCounter = 0
            for (drag in dragons) drag.reset()
        }
        DungeonEvents.onParticle { packet ->
            if (!active() || !isDragonParticle(packet) || !DungeonUtil.inDragonPhase()) return@onParticle
            val pos = Vec3(packet.x, packet.y, packet.z)
            val cfg = SkyCoreConfig.instance.witherDragons
            for (drag in dragons) {
                if (drag.spawnTicks != 0 || !drag.area.contains(pos)) continue
                drag.startTicking()
                val spawning = dragons.filter { it.isSpawning() }
                if (!splitDone && spawning.size == 2) {
                    if (cfg.alert) {
                        val power = DungeonUtil.powerLevel
                        val first = spawning[0]
                        val second = spawning[1]
                        val priority = if (
                            (power >= cfg.powerEasy && isEitherPurple(first, second)) ||
                            power >= cfg.power
                        ) {
                            getHigherPriority(first, second, isArcherTeam())
                        } else {
                            getHigherPriority(first, second, archerTeam = true)
                        }
                        announceSpawn(priority, split = true)
                    }
                    splitDone = true
                } else if (splitDone && cfg.alert) {
                    announceSpawn(drag, split = false)
                }
            }
        }
        DungeonEvents.onUpdated { entity ->
            if (!active() || !DungeonUtil.inDragonPhase()) return@onUpdated
            if (entity is ArmorStand && isIceSprayEntity(entity)) {
                if (!SkyCoreConfig.instance.witherDragons.trackIceSpray) return@onUpdated
                for (dragon in dragons) {
                    if (!dragon.hasEntity()) continue
                    val ent = dragon.getEntity() ?: continue
                    if (
                        !dragon.iceSprayed &&
                        horizontalDistance(ent, entity) < 2.0 &&
                        entity.y > ent.y
                    ) {
                        val player = Minecraft.getInstance().player ?: return@onUpdated
                        player.sendSystemMessage(
                            Component.literal(dragon.name).withColor(dragon.color)
                                .append(
                                    Component.literal(" Ice Sprayed in ${tickCounter - dragon.spawnedAt} ticks.")
                                        .withStyle(ChatFormatting.GRAY)
                                )
                        )
                        dragon.iceSprayed = true
                        break
                    }
                }
            } else {
                updateDragonEntities(entity)
            }
        }
        DungeonEvents.onServerTick {
            if (!active() || !DungeonUtil.inDragonPhase()) return@onServerTick
            for (drag in dragons) drag.tick()
            tickCounter++
        }
        WorldBoxes.onRender { _, partial ->
            if (!active() || !DungeonUtil.inDragonPhase()) return@onRender
            val cfg = SkyCoreConfig.instance.witherDragons
            for (drag in dragons) {
                if (cfg.boxes && (drag.isSpawning() || drag.hasEntity())) {
                    WorldBoxes.outline(drag.area, opaque(drag.color))
                }
                if (cfg.hitboxes && drag.hasEntity()) {
                    val dragon = drag.getEntity() ?: continue
                    WorldBoxes.outline(lerpedBox(dragon, partial), opaque(drag.color), throughWalls = false)
                }
                when (cfg.waypoints.lowercase()) {
                    "simple" -> if (drag.isSpawning()) {
                        WorldBoxes.filled(drag.pos, withAlpha(drag.color, 0.5f), throughWalls = false)
                    }
                    "advanced" -> if (drag.isSpawning()) {
                        for (part in drag.parts) {
                            WorldBoxes.filled(part, withAlpha(drag.color, 0.33f), throughWalls = false)
                        }
                    }
                }
            }
            if (cfg.tracers) {
                val spawning = dragons.filter { it.isSpawning() }
                if (spawning.isNotEmpty()) {
                    val drag = if (spawning.size == 2) {
                        getHigherPriority(spawning[0], spawning[1], isArcherTeam())
                    } else {
                        spawning[0]
                    }
                    WorldBoxes.tracer(drag.pos.center, opaque(drag.color))
                }
            }
        }
        WorldLabels.onRender { _, partial ->
            if (!active() || !DungeonUtil.inDragonPhase()) return@onRender
            val cfg = SkyCoreConfig.instance.witherDragons
            for (drag in dragons) {
                if (cfg.timer && drag.isSpawning()) {
                    val seconds = drag.spawnTicks / 20.0f
                    val text = Component.literal(String.format("%.3fs", seconds))
                    WorldLabels.text(
                        drag.pos.center.add(0.0, 4.0, 0.0),
                        text,
                        0.3f,
                        percentageColor(seconds / 5.0, inverse = true)
                    )
                }
                if (cfg.health && drag.hasEntity()) {
                    val ent = drag.getEntity() ?: continue
                    val maxHealth = if (drag.maxHealth > 0.0) drag.maxHealth else 200.0
                    val text = Component.literal(String.format("%.2fM", drag.health * 0.000001))
                    WorldLabels.text(
                        ent.getPosition(partial),
                        text,
                        0.2f,
                        percentageColor(drag.health / maxHealth, inverse = true)
                    )
                }
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.witherDragons.enabled &&
            DungeonUtil.inDungeons()

    private fun isArcherTeam(): Boolean =
        DungeonUtil.isClass("Archer") || DungeonUtil.isClass("Tank")

    private fun isDragonParticle(packet: ClientboundLevelParticlesPacket): Boolean =
        packet.particle.type == ParticleTypes.FLAME &&
            packet.count == 20 &&
            packet.y == 19.0 &&
            packet.xDist == 2.0f &&
            packet.yDist == 3.0f &&
            packet.zDist == 2.0f &&
            packet.maxSpeed == 0.0f &&
            packet.x % 1.0 == 0.0 &&
            packet.z % 1.0 == 0.0

    private fun isEitherPurple(first: Dragon, second: Dragon): Boolean =
        first === Dragon.PURPLE || second === Dragon.PURPLE

    private fun isIceSprayEntity(stand: ArmorStand): Boolean {
        if (!stand.isMarker) return false
        val item = stand.getItemBySlot(EquipmentSlot.MAINHAND)
        return item.item == Items.PACKED_ICE &&
            item.count == 1 &&
            item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) == null
    }

    private fun getHigherPriority(first: Dragon, second: Dragon, archerTeam: Boolean): Dragon =
        if (archerTeam) {
            if (first.archPriority > second.archPriority) first else second
        } else {
            if (first.bersPriority > second.bersPriority) first else second
        }

    private fun announceSpawn(drag: Dragon, split: Boolean) {
        Titles.show(
            "§l${drag.name.uppercase()} IS SPAWNING",
            fadeIn = 0,
            stay = 30,
            fadeOut = 10
        )
        Titles.playOrb()
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(
            Component.literal(drag.name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(drag.color and 0xFFFFFF)))
                .append(
                    Component.literal(if (split) " is your priority dragon." else " is spawning.")
                        .withStyle(ChatFormatting.GRAY)
                )
        )
    }

    private fun updateDragonEntities(entity: Entity) {
        for (drag in dragons) {
            if (entity is EnderDragon) {
                if (!drag.hasEntity()) {
                    for (collar in drag.collarCache.get()) {
                        if (horizontalDistance(entity, collar) <= 10.0) {
                            drag.setEntity(entity)
                            break
                        }
                    }
                } else if (drag.getEntity() === entity) {
                    drag.setEntity(entity)
                }
            } else if (entity is ArmorStand) {
                if (drag.isCollar(entity)) {
                    drag.collarCache.add(entity)
                    for (dragon in drag.dragonCache.get()) {
                        if (horizontalDistance(dragon, entity) <= 10.0) {
                            drag.setEntity(dragon as EnderDragon)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun horizontalDistance(from: Entity, to: Entity): Double {
        val dx = from.x - to.x
        val dz = from.z - to.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun lerpedBox(entity: Entity, partial: Float): AABB {
        val x = Mth.lerp(partial.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(partial.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(partial.toDouble(), entity.zOld, entity.z)
        return entity.boundingBox.move(x - entity.x, y - entity.y, z - entity.z)
    }

    private fun opaque(rgb: Int): Int = 0xFF000000.toInt() or (rgb and 0xFFFFFF)

    private fun withAlpha(rgb: Int, alpha: Float): Int =
        ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun percentageColor(percentage: Double, inverse: Boolean): Int =
        when {
            percentage > 0.66 -> if (inverse) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
            percentage > 0.33 -> 0xFFFFAA00.toInt()
            else -> if (inverse) 0xFFFF5555.toInt() else 0xFF55FF55.toInt()
        }

    private class Dragon(
        val name: String,
        val archPriority: Int,
        val bersPriority: Int,
        val relicID: String,
        val color: Int,
        val pos: AABB,
        val parts: List<AABB>,
        val area: AABB
    ) {
        val dragonCache = EntityCache.create()
        val collarCache = EntityCache.create()

        @Volatile
        var health = 0.0f

        @Volatile
        var maxHealth = 200.0

        @Volatile
        var spawnTicks = 0

        @Volatile
        var spawnedAt = 0

        @Volatile
        var iceSprayed = false

        fun isSpawning(): Boolean = spawnTicks > 0

        fun startTicking() {
            spawnTicks = 100
        }

        fun tick() {
            if (spawnTicks > 0) spawnTicks--
            if (!hasEntity()) {
                if (iceSprayed) iceSprayed = false
                if (spawnedAt != 0) spawnedAt = 0
            }
        }

        fun reset() {
            iceSprayed = false
            health = 0.0f
            maxHealth = 200.0
            spawnTicks = 0
            spawnedAt = 0
            dragonCache.clear()
            collarCache.clear()
        }

        fun hasEntity(): Boolean {
            val first = dragonCache.getFirst()
            return first != null && first.isAlive
        }

        fun getEntity(): EnderDragon? = dragonCache.getFirst() as? EnderDragon

        fun setEntity(ent: EnderDragon) {
            dragonCache.add(ent)
            health = ent.health
            maxHealth = ent.getAttributeBaseValue(Attributes.MAX_HEALTH)
            if (spawnedAt == 0) spawnedAt = tickCounter
        }

        fun isCollar(entity: ArmorStand): Boolean {
            val helmet = entity.getItemBySlot(EquipmentSlot.HEAD)
            return !helmet.isEmpty && ItemData.skyblockId(helmet) == relicID
        }

        companion object {
            val RED = Dragon(
                "Red", 3, 3, "RED_KING_RELIC", 0xff0000,
                AABB.ofSize(Vec3(27.0, 14.0, 59.0), 1.0, 1.0, 1.0),
                listOf(
                    AABB(25.5, 14.0, 52.0, 28.5, 17.0, 55.0),
                    AABB(24.5, 14.0, 56.0, 29.5, 17.0, 61.0),
                    AABB(26.0, 15.5, 61.5, 28.0, 17.5, 67.5),
                    AABB(29.5, 16.0, 57.0, 33.5, 18.0, 61.0),
                    AABB(20.5, 16.0, 57.0, 24.5, 18.0, 61.0)
                ),
                AABB(14.5, 5.0, 45.5, 39.5, 28.0, 70.5)
            )
            val ORANGE = Dragon(
                "Orange", 1, 5, "ORANGE_KING_RELIC", 0xffaa00,
                AABB.ofSize(Vec3(85.0, 14.0, 56.0), 1.0, 1.0, 1.0),
                listOf(
                    AABB(83.5, 14.0, 49.0, 86.5, 17.0, 52.0),
                    AABB(82.5, 14.0, 53.0, 87.5, 17.0, 58.0),
                    AABB(84.0, 15.5, 58.5, 86.0, 17.5, 64.5),
                    AABB(87.5, 16.0, 54.0, 91.5, 18.0, 58.0),
                    AABB(78.5, 16.0, 54.0, 82.5, 18.0, 58.0)
                ),
                AABB(72.0, 5.0, 47.0, 102.0, 28.0, 77.0)
            )
            val BLUE = Dragon(
                "Blue", 4, 2, "BLUE_KING_RELIC", 0x55ffff,
                AABB.ofSize(Vec3(84.0, 14.0, 94.0), 1.0, 1.0, 1.0),
                listOf(
                    AABB(82.5, 14.0, 87.0, 85.5, 17.0, 90.0),
                    AABB(81.5, 14.0, 91.0, 86.5, 17.0, 96.0),
                    AABB(83.0, 15.5, 96.5, 85.0, 17.5, 102.5),
                    AABB(86.5, 16.0, 92.0, 90.5, 18.0, 96.0),
                    AABB(77.5, 16.0, 92.0, 81.5, 18.0, 96.0)
                ),
                AABB(71.5, 5.0, 82.5, 96.5, 26.0, 107.5)
            )
            val PURPLE = Dragon(
                "Purple", 5, 1, "PURPLE_KING_RELIC", 0xaa00aa,
                AABB.ofSize(Vec3(56.0, 14.0, 125.0), 1.0, 1.0, 1.0),
                listOf(
                    AABB(54.5, 14.0, 118.0, 57.5, 17.0, 121.0),
                    AABB(53.5, 14.0, 122.0, 58.5, 17.0, 127.0),
                    AABB(55.0, 15.5, 127.5, 57.0, 17.5, 133.5),
                    AABB(58.5, 16.0, 123.0, 62.5, 18.0, 127.0),
                    AABB(49.5, 16.0, 123.0, 53.5, 18.0, 127.0)
                ),
                AABB(45.5, 6.0, 113.5, 68.5, 23.0, 136.5)
            )
            val GREEN = Dragon(
                "Green", 2, 4, "GREEN_KING_RELIC", 0x00ff00,
                AABB.ofSize(Vec3(27.0, 14.0, 94.0), 1.0, 1.0, 1.0),
                listOf(
                    AABB(25.5, 14.0, 87.0, 28.5, 17.0, 90.0),
                    AABB(24.5, 14.0, 91.0, 29.5, 17.0, 96.0),
                    AABB(26.0, 15.5, 96.5, 28.0, 17.5, 102.5),
                    AABB(29.5, 16.0, 92.0, 33.5, 18.0, 96.0),
                    AABB(20.5, 16.0, 92.0, 24.5, 18.0, 96.0)
                ),
                AABB(7.0, 5.0, 80.0, 37.0, 28.0, 110.0)
            )
        }
    }
}
