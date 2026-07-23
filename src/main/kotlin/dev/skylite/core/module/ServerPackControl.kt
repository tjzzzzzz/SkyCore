package dev.skylite.core.module

import dev.skylite.SkyLite
import dev.skylite.config.SkyLiteConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * decides whether a server pushed resource pack should be rejected.
 *
 * the actual interception lives in [dev.skylite.mixin.client.ResourcePackPushMixin];
 * this object is just the policy it consults, kept in kotlin so the config and
 * any future per-server rules stay next to the rest of the module code.
 */
object ServerPackControl {

    private var reconciled = false

    /**
     * on the first tick, if the feature is already enabled but the pack never
     * made it to disk (fresh install, deleted file), fetch it. runs once, off
     * the init path since Minecraft is not ready during onInitializeClient.
     */
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (reconciled) return@register
            reconciled = true
            val options = SkyLiteConfig.instance.serverPack
            if (options.disable) LegacyPackInstaller.ensureInstalled(options.autoEnableLegacy)
        }
    }

    /**
     * true when the ordering mixins should unpin the server pack so the legacy
     * pack layers on top of it.
     *
     * only ever active on hypixel. reordering server packs on arbitrary servers
     * could break ones that genuinely need theirs to win, so the gate is narrow.
     */
    fun isReordering(): Boolean {
        if (!SkyLiteConfig.instance.serverPack.disable) return false
        return onHypixel()
    }

    private fun onHypixel(): Boolean {
        val server = Minecraft.getInstance().currentServer ?: return false
        val ip = server.ip.lowercase()
        return ip.contains("hypixel.net") || ip.contains("hypixel.io")
    }

    /**
     * rebuilds the resource stack so an ordering change takes effect now instead
     * of on the next server pack push. only meaningful while connected.
     */
    fun refresh() {
        if (Minecraft.getInstance().currentServer == null) return
        SkyLite.logger.debug("reloading resources to re-layer packs")
        Minecraft.getInstance().reloadResourcePacks()
    }
}
