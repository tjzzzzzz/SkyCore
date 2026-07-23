package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

object MinibossHighlight {

    private const val OUTLINE = 0xFFFFFF00.toInt()
    private const val FILL = 0x80FFFF00.toInt()

    private val names = setOf(
        "Lost Adventurer",
        "Diamond Guy",
        "Shadow Assassin",
        "King Midas",
        "Spirit Bear"
    )

    val cache: EntityCache = EntityCache.create()

    fun init() {
        DungeonEvents.onUpdated { entity ->
            if (!active()) return@onUpdated
            if (isMiniboss(entity)) cache.add(entity)
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
            SkyCoreConfig.instance.minibossHighlight.enabled &&
            DungeonUtil.inDungeons()

    private fun isMiniboss(ent: Entity): Boolean {
        if (ent !is Player || DungeonUtil.isRealPlayer(ent)) return false
        val name = ent.name.string
        if (name !in names) return false
        return if (DungeonUtil.inBossRoom("4")) {
            ent.y < 76.0
        } else {
            name != "Spirit Bear"
        }
    }

    private fun lerpedBox(entity: Entity, partial: Float): AABB {
        val x = Mth.lerp(partial.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(partial.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(partial.toDouble(), entity.zOld, entity.z)
        return entity.boundingBox.move(x - entity.x, y - entity.y, z - entity.z)
    }
}
