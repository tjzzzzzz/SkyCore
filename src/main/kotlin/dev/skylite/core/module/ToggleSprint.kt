package dev.skylite.core.module

import dev.skylite.config.SkyLiteConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * holds the sprint key down for the player.
 *
 * this is the vanilla-legit way to do toggle sprint: we press the same
 * KeyMapping the player would, and let the game decide whether that actually
 * results in sprinting. vanilla will refuse if you are not moving forward, are
 * sneaking, are too hungry, or are using an item, exactly as if you were
 * holding the key yourself. nothing here touches movement, speed or physics.
 */
object ToggleSprint {

    private val enabled: Boolean
        get() = SkyLiteConfig.instance.toggleSprint.enabled

    /** true while we are actively forcing the key, drives the hud pill */
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

        // keyUp only reads down when no screen has input focus, so a "w" typed
        // into chat already reports false here, no screen check needed
        val sprintKey = client.options.keySprint
        val forwardHeld = client.options.keyUp.isDown

        // press sprint only when moving forward. vanilla ignores a sprint press
        // that is not paired with forward movement anyway, but gating here keeps
        // the hud honest and avoids fighting the player when they walk backward.
        if (forwardHeld) {
            sprintKey.setDown(true)
            active = true
        } else {
            // release so the player stops sprinting the instant they stop, same
            // as letting go of the key
            sprintKey.setDown(false)
            active = false
        }
    }
}
