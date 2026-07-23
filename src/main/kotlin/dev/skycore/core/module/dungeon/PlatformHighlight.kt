package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.render.WorldBoxes
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

object PlatformHighlight {

    private const val OUTLINE = 0xFF55FF55.toInt()
    private const val FILL = 0x8055FF55.toInt()

    private val box = AABB.encapsulatingFullBlocks(BlockPos(55, 63, 115), BlockPos(53, 63, 113))

    fun init() {
        WorldBoxes.onRender { _, _ ->
            if (!active()) return@onRender
            val cfg = SkyCoreConfig.instance.platformHighlight
            if (cfg.healerOnly && !DungeonUtil.isClass("Healer")) return@onRender
            WorldBoxes.both(box, FILL, OUTLINE, throughWalls = false)
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.platformHighlight.enabled &&
            DungeonUtil.inBossRoom("7")
}
