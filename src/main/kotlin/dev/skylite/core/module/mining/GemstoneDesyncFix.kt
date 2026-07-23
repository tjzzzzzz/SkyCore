package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.TabListCache
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CrossCollisionBlock
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.StainedGlassPaneBlock
import net.minecraft.world.level.block.state.BlockState

object GemstoneDesyncFix {

    fun init() {}

    fun active(): Boolean {
        if (!SkyLiteConfig.instance.enabled || !SkyLiteConfig.instance.gemstoneDesyncFix.enabled) return false
        val island = LocationManager.current
        if (island == IslandType.DWARVEN_MINES ||
            island == IslandType.CRYSTAL_HOLLOWS ||
            island == IslandType.GLACITE_MINESHAFTS ||
            island == IslandType.CRIMSON_ISLE
        ) return true
        val area = TabListCache.area
        return area == "Dwarven Mines" ||
            area == "Crystal Hollows" ||
            area.contains("Mineshaft") ||
            area == "Crimson Isle" ||
            area == "The Rift"
    }

    fun isStainedGlass(state: BlockState): Boolean {
        val block = state.block
        return block is StainedGlassBlock || block is StainedGlassPaneBlock
    }

    fun isConnectedPane(state: BlockState): Boolean =
        state.getValue(CrossCollisionBlock.NORTH) ||
            state.getValue(CrossCollisionBlock.EAST) ||
            state.getValue(CrossCollisionBlock.SOUTH) ||
            state.getValue(CrossCollisionBlock.WEST)

    fun isDefaultPane(state: BlockState): Boolean =
        isStainedGlass(state) && !isConnectedPane(state)

    fun asFullPane(state: BlockState): BlockState =
        state
            .setValue(CrossCollisionBlock.NORTH, true)
            .setValue(CrossCollisionBlock.EAST, true)
            .setValue(CrossCollisionBlock.SOUTH, true)
            .setValue(CrossCollisionBlock.WEST, true)

    fun onBlockUpdate(pos: BlockPos, oldState: BlockState, newState: BlockState) {
        if (!active()) return
        if (newState.isAir && isStainedGlass(oldState)) {
            val level = Minecraft.getInstance().level ?: return
            newState.updateNeighbourShapes(level, pos, Block.UPDATE_ALL)
        }
    }
}
