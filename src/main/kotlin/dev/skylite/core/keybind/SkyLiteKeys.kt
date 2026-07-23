package dev.skylite.core.keybind

import com.mojang.blaze3d.platform.InputConstants
import dev.skylite.SkyLite
import dev.skylite.config.SkyLiteConfig
import dev.skylite.ui.clickgui.SkyLiteClickGui
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping

/**
 * mod keybinds, owned by the skylite config rather than options.txt.
 *
 * the mappings are still registered with vanilla so they show up in the normal
 * controls screen and take part in conflict detection, but the binding itself is
 * mirrored into [SkyLiteConfig.keybinds]. that means one config file carries a
 * player's whole setup, and a rebind made from either side survives.
 */
object SkyLiteKeys {

    /** how often to look for a rebind made through the vanilla controls screen */
    private const val SYNC_INTERVAL_TICKS = 20

    private val managed = mutableListOf<KeyMapping>()

    lateinit var openClickGui: KeyMapping
        private set

    /**
     * vanilla loads options.txt during startup, and it would stomp anything we
     * applied from init. so the first apply waits for a tick, by which point the
     * game is fully up.
     */
    private var applied = false
    private var syncCooldown = SYNC_INTERVAL_TICKS

    fun init() {
        openClickGui = register(
            KeyMapping(
                "key.skylite.clickgui",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                KeyMapping.Category.MISC
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!applied) {
                applyFromConfig()
                applied = true
            }

            if (--syncCooldown <= 0) {
                syncCooldown = SYNC_INTERVAL_TICKS
                captureRebinds()
            }

            // consumeClick drains the queue, so a held key does not reopen the gui.
            // it only ever fills from gameplay input, so there is no need to check
            // for an open screen first, 26.2 no longer exposes one from Minecraft.
            while (openClickGui.consumeClick()) {
                client.setScreenAndShow(SkyLiteClickGui())
            }
        }
    }

    private fun register(mapping: KeyMapping): KeyMapping {
        val registered = KeyMappingHelper.registerKeyMapping(mapping)
        managed += registered
        return registered
    }

    /** pushes the stored binds onto the live mappings */
    private fun applyFromConfig() {
        val stored = SkyLiteConfig.instance.keybinds
        if (stored.isEmpty()) {
            // nothing saved yet, seed the file from the defaults
            captureRebinds()
            return
        }

        var changed = false
        for (mapping in managed) {
            val saved = stored[mapping.name] ?: continue
            val key = runCatching { InputConstants.getKey(saved) }.getOrNull()
            if (key == null) {
                SkyLite.logger.warn("ignoring unparseable keybind '{}' for {}", saved, mapping.name)
                continue
            }
            if (key != KeyMappingHelper.getBoundKeyOf(mapping)) {
                mapping.setKey(key)
                changed = true
            }
        }

        // rebuilds vanilla's key lookup table, without this the new bind is dead
        if (changed) KeyMapping.resetMapping()
    }

    /** human readable name of whatever [mapping] is currently bound to */
    fun labelOf(mapping: KeyMapping): String =
        KeyMappingHelper.getBoundKeyOf(mapping).displayName.string

    /**
     * rebinds from our own ui. pass [InputConstants.UNKNOWN] to clear a bind.
     * writes straight through to the config so nothing is lost on a crash.
     */
    fun rebind(mapping: KeyMapping, key: InputConstants.Key) {
        mapping.setKey(key)
        KeyMapping.resetMapping()
        captureRebinds()
    }

    /** writes back anything the player rebound through the controls screen */
    private fun captureRebinds() {
        val stored = SkyLiteConfig.instance.keybinds
        var dirty = false

        for (mapping in managed) {
            val current = mapping.saveString()
            if (stored[mapping.name] != current) {
                stored[mapping.name] = current
                dirty = true
            }
        }

        if (dirty) {
            SkyLiteConfig.save()
            SkyLite.logger.debug("keybinds written to config")
        }
    }
}
