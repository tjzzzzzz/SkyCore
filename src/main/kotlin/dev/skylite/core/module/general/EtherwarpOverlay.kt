package dev.skylite.core.module.general

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.render.WorldBoxes
import dev.skylite.core.skyblock.ItemData
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object EtherwarpOverlay {

    private const val BASE_DISTANCE = 57
    private const val FILL_OK = 0x7F00FF00
    private const val OUTLINE_OK = 0xFF00FF00.toInt()
    private const val FILL_BAD = 0x7FFF0000
    private const val OUTLINE_BAD = 0xFFFF0000.toInt()

    fun init() {
        WorldBoxes.onRender { _, partial ->
            if (!enabled()) return@onRender
            val dist = warpDistance()
            if (dist <= 0) return@onRender
            val pos = raycast(dist, partial) ?: return@onRender
            val valid = isFloorValid(pos) && isBodyValid(pos.above()) && isBodyValid(pos.above(2))
            val box = AABB.encapsulatingFullBlocks(pos, pos)
            if (valid) {
                WorldBoxes.both(box, FILL_OK, OUTLINE_OK)
            } else {
                WorldBoxes.both(box, FILL_BAD, OUTLINE_BAD)
            }
        }
    }

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.etherwarpOverlay.enabled

    private fun warpDistance(): Int {
        val client = Minecraft.getInstance()
        val player = client.player ?: return 0
        val stack = player.mainHandItem
        if (stack.isEmpty) return 0
        val data = ItemData.customData(stack)
        val id = ItemData.skyblockId(stack)
        if (id.isEmpty()) return 0
        val tuned = data.getIntOr("tuned_transmission", 0)
        if (id == "ETHERWARP_CONDUIT") return BASE_DISTANCE + tuned
        if ((id == "ASPECT_OF_THE_VOID" || id == "ASPECT_OF_THE_END") &&
            data.getByteOr("ethermerge", 0.toByte()) == 1.toByte() &&
            client.options.keyShift.isDown
        ) {
            return BASE_DISTANCE + tuned
        }
        return 0
    }

    private fun raycast(distance: Int, partial: Float): BlockPos? {
        val player = Minecraft.getInstance().player ?: return null
        val level = Minecraft.getInstance().level ?: return null
        val eye = player.getEyePosition(partial)
        val look = player.getViewVector(partial)
        val end = eye.add(look.x * distance, look.y * distance, look.z * distance)
        val hit = level.clip(
            ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
        )
        if (hit.type != HitResult.Type.BLOCK) return null
        return (hit as BlockHitResult).blockPos
    }

    private fun isFloorValid(pos: BlockPos): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val state = level.getBlockState(pos)
        if (state.isAir || state.block == Blocks.AIR) return false
        return state.canOcclude() || state.isCollisionShapeFullBlock(level, pos)
    }

    private fun isBodyValid(pos: BlockPos): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val state = level.getBlockState(pos)
        return state.isAir || !state.canOcclude()
    }
}
