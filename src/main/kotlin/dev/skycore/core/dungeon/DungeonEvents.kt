package dev.skycore.core.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.CopyOnWriteArrayList

object DungeonEvents {

    private val named = CopyOnWriteArrayList<(Entity, Component, String) -> Unit>()
    private val updated = CopyOnWriteArrayList<(Entity) -> Unit>()
    private val removed = CopyOnWriteArrayList<(Entity) -> Unit>()
    private val blockUpdates = CopyOnWriteArrayList<(BlockPos, BlockState, BlockState) -> Unit>()
    private val particles = CopyOnWriteArrayList<(ClientboundLevelParticlesPacket) -> Unit>()
    private val serverTicks = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var serverTick: Int = 0
        private set

    fun onNamed(listener: (Entity, Component, String) -> Unit) {
        named += listener
    }

    fun onUpdated(listener: (Entity) -> Unit) {
        updated += listener
    }

    fun onRemoved(listener: (Entity) -> Unit) {
        removed += listener
    }

    fun onBlockUpdate(listener: (BlockPos, BlockState, BlockState) -> Unit) {
        blockUpdates += listener
    }

    fun onParticle(listener: (ClientboundLevelParticlesPacket) -> Unit) {
        particles += listener
    }

    fun onServerTick(listener: () -> Unit) {
        serverTicks += listener
    }

    fun fireNamed(entity: Entity, name: Component, plain: String) {
        for (listener in named) listener(entity, name, plain)
    }

    fun fireUpdated(entity: Entity) {
        EntityCache.onEntityUpdated(entity)
        for (listener in updated) listener(entity)
    }

    fun fireRemoved(entity: Entity) {
        EntityCache.onEntityRemoved(entity)
        for (listener in removed) listener(entity)
    }

    fun fireBlockUpdate(pos: BlockPos, oldState: BlockState, newState: BlockState) {
        for (listener in blockUpdates) listener(pos, oldState, newState)
    }

    fun fireParticle(packet: ClientboundLevelParticlesPacket) {
        for (listener in particles) listener(packet)
    }

    fun fireServerTick() {
        serverTick++
        for (listener in serverTicks) listener()
    }

    fun resetTick() {
        serverTick = 0
    }
}
