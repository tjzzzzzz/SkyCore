package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB

object StarredMobHighlight {

    private const val STAR = '\u272F'
    private const val OUTLINE = 0xFF00FFFF.toInt()
    private const val FILL = 0x8000FFFF.toInt()

    private val cache = EntityCache.create()

    fun init() {
        DungeonEvents.onNamed { entity, _, plain ->
            if (!active()) return@onNamed
            if (!isStarred(plain)) return@onNamed
            val others = DungeonUtil.nearby(entity, 0.5, 2.0, 0.5, ::isDungeonMob)
            val closest = DungeonUtil.findNametagOwner(entity, others) ?: return@onNamed
            if (!MinibossHighlight.cache.has(closest)) {
                cache.add(closest)
            }
        }
        WorldBoxes.onRender { _, partial ->
            if (!active()) return@onRender
            for (ent in cache.get()) {
                if (!ent.isAlive) continue
                WorldBoxes.both(lerpedBox(ent, partial), FILL, OUTLINE, throughWalls = false)
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.starredMobHighlight.enabled &&
            DungeonUtil.inDungeons()

    private fun isStarred(name: String): Boolean {
        val index = name.indexOf(STAR)
        return index != -1 && index == name.lastIndexOf(STAR)
    }

    private fun isDungeonMob(entity: Entity): Boolean {
        if (entity is ArmorStand) return false
        return DungeonUtil.isMob(entity) && !cache.has(entity)
    }

    private fun lerpedBox(entity: Entity, partial: Float): AABB {
        val x = Mth.lerp(partial.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(partial.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(partial.toDouble(), entity.zOld, entity.z)
        return entity.boundingBox.move(x - entity.x, y - entity.y, z - entity.z)
    }
}
