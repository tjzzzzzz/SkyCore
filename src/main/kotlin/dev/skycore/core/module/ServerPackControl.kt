package dev.skycore.core.module

import dev.skycore.SkyCore
import dev.skycore.config.SkyCoreConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object ServerPackControl {

    private var reconciled = false

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (reconciled) return@register
            reconciled = true
            val options = SkyCoreConfig.instance.serverPack
            if (options.disable) LegacyPackInstaller.ensureInstalled(options.autoEnableLegacy)
        }
    }

    fun isReordering(): Boolean {
        if (!SkyCoreConfig.instance.serverPack.disable) return false
        return onHypixel()
    }

    private fun onHypixel(): Boolean {
        val server = Minecraft.getInstance().currentServer ?: return false
        val ip = server.ip.lowercase()
        return ip.contains("hypixel.net") || ip.contains("hypixel.io")
    }

    fun refresh() {
        if (Minecraft.getInstance().currentServer == null) return
        SkyCore.logger.debug("reloading resources to re-layer packs")
        Minecraft.getInstance().reloadResourcePacks()
    }
}
