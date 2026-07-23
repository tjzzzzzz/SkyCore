package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.render.WorldLabels
import dev.skycore.core.skyblock.ItemData
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object TerracottaTimer {

    private const val SADAN =
        "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!"

    private val terracottas = ConcurrentHashMap.newKeySet<Terracotta>()

    @Volatile
    private var gyroTicks = -1

    @Volatile
    private var gyroStart = 0

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            terracottas.clear()
            gyroTicks = -1
            gyroStart = 0
        }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !active() || !DungeonUtil.onFloor("6")) return@register
            if (ItemData.plain(message).trim() != SADAN) return@register
            startGyro(267)
        }
        DungeonEvents.onBlockUpdate { pos, oldState, newState ->
            if (!active() || !DungeonUtil.inBossRoom("6") || !oldState.isAir) return@onBlockUpdate
            if (newState.block is FlowerPotBlock) {
                terracottas.add(Terracotta(pos, if (DungeonUtil.onFloor("M6")) 240 else 300))
            }
            if (!isGyroTicking() && newState.block == Blocks.NETHER_BRICK_FENCE) {
                startGyro(235)
            }
        }
        DungeonEvents.onServerTick {
            if (!active() || !DungeonUtil.inBossRoom("6")) return@onServerTick
            for (terra in terracottas) terra.tick()
            if (gyroTicks > 0) {
                gyroTicks--
                if (gyroTicks == 0) gyroTicks = -1
            }
        }
        WorldLabels.onRender { _, _ ->
            if (!active() || !DungeonUtil.inBossRoom("6")) return@onRender
            val level = Minecraft.getInstance().level ?: return@onRender
            val scale = (SkyCoreConfig.instance.terracottaTimer.scale * 0.1f).toFloat()
            val color = 0xFFFFFF00.toInt()
            val iter = terracottas.iterator()
            while (iter.hasNext()) {
                val terra = iter.next()
                if (terra.ticks == 0 || level.getBlockState(terra.pos).isAir) {
                    iter.remove()
                    continue
                }
                val text = Component.literal(String.format("%.2fs", terra.ticks / 20.0f))
                WorldLabels.text(
                    Vec3(terra.pos.x + 0.5, terra.pos.y + 0.5, terra.pos.z + 0.5),
                    text,
                    scale,
                    color
                )
            }
        }
    }

    fun isGyroTicking(): Boolean = gyroTicks != -1

    fun gyroSeconds(): Float = if (gyroTicks < 0) 0f else gyroTicks / 20.0f

    fun gyroRatio(): Double {
        if (gyroTicks < 0 || gyroStart <= 0) return 0.0
        return gyroTicks.toDouble() / gyroStart.toDouble()
    }

    private fun startGyro(ticks: Int) {
        gyroStart = ticks
        gyroTicks = ticks
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.terracottaTimer.enabled &&
            DungeonUtil.inDungeons()

    private class Terracotta(val pos: BlockPos, var ticks: Int) {
        fun tick() {
            if (ticks > 0) ticks--
        }

        override fun equals(other: Any?): Boolean =
            other is Terracotta && pos == other.pos

        override fun hashCode(): Int = pos.hashCode()
    }
}
