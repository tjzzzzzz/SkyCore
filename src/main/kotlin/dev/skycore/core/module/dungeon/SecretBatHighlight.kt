package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB

object SecretBatHighlight {

    private const val OUTLINE = 0xFF55FF55.toInt()
    private const val FILL = 0x7F55FF55

    private val cache = EntityCache.create()

    fun init() {
        DungeonEvents.onUpdated { entity ->
            if (!active()) return@onUpdated
            if (DungeonUtil.isSecretBat(entity)) cache.add(entity)
        }
        WorldBoxes.onRender { _, partial ->
            if (!active() || cache.empty()) return@onRender
            for (ent in cache.get()) {
                if (!ent.isAlive) continue
                WorldBoxes.both(lerpedBox(ent, partial), FILL, OUTLINE, throughWalls = false)
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.secretBatHighlight.enabled &&
            DungeonUtil.inDungeons()

    private fun lerpedBox(entity: Entity, partial: Float): AABB {
        val x = Mth.lerp(partial.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(partial.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(partial.toDouble(), entity.zOld, entity.z)
        return entity.boundingBox.move(x - entity.x, y - entity.y, z - entity.z)
    }
}
