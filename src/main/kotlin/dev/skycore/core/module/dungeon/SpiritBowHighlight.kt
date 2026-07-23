package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object SpiritBowHighlight {

    private const val COLOR = 0xAAAF00FF.toInt()

    private val cache = EntityCache.create()

    fun init() {
        DungeonEvents.onNamed { entity, _, plain ->
            if (!active()) return@onNamed
            if (entity is ArmorStand && plain == "Spirit Bow") {
                cache.add(entity)
            }
        }
        WorldBoxes.onRender { _, _ ->
            if (!active() || cache.empty()) return@onRender
            for (ent in cache.get()) {
                val ground = DungeonUtil.findGround(ent.blockPosition(), 4)
                val pos = ent.position()
                val adjusted = Vec3(pos.x, ground.above(1).y + 1.0, pos.z)
                WorldBoxes.filled(AABB.ofSize(adjusted, 0.8, 1.75, 0.8), COLOR)
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.spiritBowHighlight.enabled &&
            DungeonUtil.inDungeons()
}
