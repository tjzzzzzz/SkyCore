package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.skyblock.ItemData
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB

object RelicHighlight {

    private val green = Relic(49, 7, 44, 0xFF00FF00.toInt())
    private val red = Relic(51, 7, 42, 0xFFFF0000.toInt())
    private val purple = Relic(54, 7, 41, 0xFFAA00AA.toInt())
    private val orange = Relic(57, 7, 42, 0xFFFFAA00.toInt())
    private val blue = Relic(59, 7, 44, 0xFF55FFFF.toInt())

    fun init() {
        WorldBoxes.onRender { _, _ ->
            if (!active()) return@onRender
            val player = Minecraft.getInstance().player ?: return@onRender
            val stack = player.inventory.getItem(8)
            if (stack.isEmpty || stack.item != Items.PLAYER_HEAD) return@onRender
            val relic = when (ItemData.plain(stack.hoverName)) {
                "Corrupted Green Relic" -> green
                "Corrupted Red Relic" -> red
                "Corrupted Purple Relic" -> purple
                "Corrupted Orange Relic" -> orange
                "Corrupted Blue Relic" -> blue
                else -> null
            } ?: return@onRender
            WorldBoxes.filled(relic.box, relic.color, throughWalls = false)
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.relicHighlight.enabled &&
            DungeonUtil.inDragonPhase()

    private class Relic(x: Int, y: Int, z: Int, val color: Int) {
        val box: AABB = AABB.encapsulatingFullBlocks(BlockPos(x, y, z), BlockPos(x, y, z))
    }
}
