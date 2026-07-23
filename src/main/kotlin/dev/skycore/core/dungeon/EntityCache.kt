package dev.skycore.core.dungeon

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class EntityCache private constructor() {

    private val entities = ConcurrentHashMap.newKeySet<Entity>()

    fun has(entity: Entity): Boolean = entities.contains(entity)

    fun empty(): Boolean = entities.isEmpty()

    fun size(): Int = entities.size

    fun add(entity: Entity) {
        entities.add(entity)
    }

    fun remove(entity: Entity) {
        entities.remove(entity)
    }

    fun removeIf(predicate: (Entity) -> Boolean) {
        entities.removeIf(predicate)
    }

    fun clear() {
        entities.clear()
    }

    fun get(): Collection<Entity> = entities

    fun getFirst(): Entity? = entities.firstOrNull()

    companion object {
        private val all = CopyOnWriteArrayList<EntityCache>()
        private var hooked = false

        fun create(): EntityCache {
            ensureHooks()
            val cache = EntityCache()
            all += cache
            return cache
        }

        fun onEntityRemoved(entity: Entity) {
            for (cache in all) cache.remove(entity)
        }

        fun onEntityUpdated(entity: Entity) {
            if (entity.isRemoved) onEntityRemoved(entity)
        }

        private fun ensureHooks() {
            if (hooked) return
            hooked = true
            ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
                for (cache in all) cache.clear()
            }
            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                for (cache in all) cache.clear()
            }
        }
    }
}
