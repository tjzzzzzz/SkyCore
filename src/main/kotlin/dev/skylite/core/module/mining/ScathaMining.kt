package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.ItemData
import dev.skylite.core.skyblock.TabListCache
import dev.skylite.core.skyblock.Titles
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap

object ScathaMining {

    private val seen = ConcurrentHashMap.newKeySet<Int>()
    private var spawnCooldown = 0

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> spawnCooldown = 0 }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !active()) return@register
            if (ItemData.plain(message) == "You hear the sound of something approaching...") {
                spawnCooldown = 620
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (spawnCooldown <= 0) return@register
            spawnCooldown--
            if (spawnCooldown == 0 && active() && SkyLiteConfig.instance.scathaMining.cooldown) {
                Titles.show("COOLDOWN ENDED", stay = 20)
                Titles.success("Worm spawn cooldown ended!")
                Titles.playHarp()
            }
        }
    }

    fun onNamed(entity: Entity, name: Component) {
        if (!active()) return
        val plain = ItemData.plain(name)
        val type = wormType(plain)
        if (type == WormType.None || seen.contains(entity.id) || !withinRadius(entity)) return
        seen.add(entity.id)
        if (SkyLiteConfig.instance.scathaMining.alert) {
            if (type == WormType.Scatha) {
                Titles.show("Scatha", stay = 20)
                Titles.playPling()
            } else {
                Titles.show("Worm", stay = 20)
                Titles.playBass()
            }
        }
    }

    private fun active(): Boolean =
        SkyLiteConfig.instance.enabled &&
            SkyLiteConfig.instance.scathaMining.enabled &&
            (LocationManager.current == IslandType.CRYSTAL_HOLLOWS || TabListCache.isInArea("Crystal Hollows"))

    private fun wormType(name: String): WormType {
        if (!name.contains('\u2756') && !name.endsWith("\u2756")) return WormType.None
        if (name.startsWith("[Lv10] Scatha ")) return WormType.Scatha
        if (name.startsWith("[Lv5] Worm ")) return WormType.Worm
        return WormType.None
    }

    private fun withinRadius(entity: Entity): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val worm = entity.blockPosition()
        val pos = player.blockPosition()
        return kotlin.math.abs(worm.y - pos.y) <= 4 &&
            (kotlin.math.abs(worm.x - pos.x) <= 2 || kotlin.math.abs(worm.z - pos.z) <= 2)
    }

    private enum class WormType { Scatha, Worm, None }
}
