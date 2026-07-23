package dev.skycore.core.module.mining

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.location.IslandType
import dev.skycore.core.location.LocationManager
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.skyblock.TabListCache
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap

object EndNodeHighlight {

    private val nodes = ConcurrentHashMap.newKeySet<BlockPos>()

    private val OFFSETS = listOf(
        BlockPos(1, 0, 0),
        BlockPos(-1, 0, 0),
        BlockPos(0, 1, 0),
        BlockPos(0, -1, 0),
        BlockPos(0, 0, 1),
        BlockPos(0, 0, -1)
    )

    private const val FILL = 0x8000FF00.toInt()
    private const val OUTLINE = 0xFF00FF00.toInt()

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> nodes.clear() }
        WorldBoxes.onRender { _, _ ->
            if (!active() || nodes.isEmpty()) return@onRender
            val level = Minecraft.getInstance().level ?: return@onRender
            val iter = nodes.iterator()
            while (iter.hasNext()) {
                val pos = iter.next()
                if (level.getBlockState(pos).block != Blocks.DYED_TERRACOTTA.purple()) {
                    iter.remove()
                    continue
                }
                WorldBoxes.filled(AABB.encapsulatingFullBlocks(pos, pos), FILL)
                WorldBoxes.outline(AABB.encapsulatingFullBlocks(pos, pos), OUTLINE)
            }
        }
    }

    fun onParticle(packet: ClientboundLevelParticlesPacket) {
        if (!active()) return
        if (packet.particle.type != ParticleTypes.WITCH) return
        if (!isNodeParticle(packet)) return
        val level = Minecraft.getInstance().level ?: return
        val origin = BlockPos.containing(packet.x, packet.y, packet.z)
        for (offset in OFFSETS) {
            val pos = origin.offset(offset)
            if (!nodes.contains(pos) && level.getBlockState(pos).block == Blocks.DYED_TERRACOTTA.purple()) {
                nodes.add(pos)
                break
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.endNodeHighlight.enabled &&
            (LocationManager.current == IslandType.THE_END || TabListCache.isInArea("The End"))

    private fun isNodeParticle(packet: ClientboundLevelParticlesPacket): Boolean =
        packet.alwaysShow() &&
            packet.isOverrideLimiter &&
            packet.count == 2 &&
            packet.maxSpeed == 0f &&
            (packet.xDist == 0.25f || packet.yDist == 0.25f || packet.zDist == 0.25f)
}
