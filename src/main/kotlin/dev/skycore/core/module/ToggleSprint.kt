package dev.skycore.core.module

import dev.skycore.config.SkyCoreConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object ToggleSprint {

    private val enabled: Boolean
        get() = SkyCoreConfig.instance.toggleSprint.enabled

    @Volatile
    var active: Boolean = false
        private set

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: Minecraft) {
        if (!enabled || client.player == null) {
            active = false
            return
        }

        val sprintKey = client.options.keySprint
        val forwardHeld = client.options.keyUp.isDown

        if (forwardHeld) {
            sprintKey.setDown(true)
            active = true
        } else {

            sprintKey.setDown(false)
            active = false
        }
    }
}
