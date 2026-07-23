package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import net.minecraft.world.phys.AABB

object KeyHighlight {

    private const val COLOR = 0x8000FF00.toInt()
    private const val BEAM = 0x5500FF00

    private val cache = EntityCache.create()

    fun init() {
        DungeonEvents.onNamed { entity, _, plain ->
            if (!active()) return@onNamed
            if (plain == "Wither Key" || plain == "Blood Key") {
                cache.add(entity)
            }
        }
        WorldBoxes.onRender { _, _ ->
            if (!active()) return@onRender
            for (key in cache.get()) {
                val box = AABB.ofSize(key.position().add(0.0, 1.5, 0.0), 1.0, 1.0, 1.0)
                WorldBoxes.filled(box, COLOR)
                val beam = AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + 256.0, box.maxZ)
                WorldBoxes.filled(beam, BEAM)
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.keyHighlight.enabled &&
            DungeonUtil.inDungeons()
}
