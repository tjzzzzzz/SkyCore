package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.render.WorldBoxes
import dev.skylite.core.skyblock.TabListCache
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap

object GhostVision {

    private val ghosts = ConcurrentHashMap.newKeySet<Int>()

    private const val FILL = 0x8000C8C8.toInt()
    private const val OUTLINE = 0xFF00C8C8.toInt()

    fun init() {
        WorldBoxes.onRender { _, partial ->
            if (!active()) return@onRender
            val level = Minecraft.getInstance().level ?: return@onRender
            for (id in ghosts) {
                val ent = level.getEntity(id) ?: continue
                if (!ent.isAlive) {
                    ghosts.remove(id)
                    continue
                }
                WorldBoxes.both(lerpedBox(ent, partial), FILL, OUTLINE)
            }
        }
    }

    fun onEntity(entity: Entity) {
        if (!active()) return
        if (entity is Creeper && entity.y < 100.0) {
            ghosts.add(entity.id)
        }
    }

    fun isGhost(creeper: Creeper): Boolean =
        SkyLiteConfig.instance.ghostVision.enabled && ghosts.contains(creeper.id)

    private fun active(): Boolean =
        SkyLiteConfig.instance.enabled &&
            SkyLiteConfig.instance.ghostVision.enabled &&
            (LocationManager.current == IslandType.DWARVEN_MINES || TabListCache.isInArea("Dwarven Mines"))

    private fun lerpedBox(entity: Entity, partial: Float): AABB {
        val x = Mth.lerp(partial.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(partial.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(partial.toDouble(), entity.zOld, entity.z)
        val dx = x - entity.x
        val dy = y - entity.y
        val dz = z - entity.z
        return entity.boundingBox.move(dx, dy, dz)
    }
}
