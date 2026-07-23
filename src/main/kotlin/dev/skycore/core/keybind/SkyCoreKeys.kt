package dev.skycore.core.keybind

import com.mojang.blaze3d.platform.InputConstants
import dev.skycore.SkyCore
import dev.skycore.config.SkyCoreConfig
import dev.skycore.ui.clickgui.SkyCoreClickGui
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping

object SkyCoreKeys {

    private const val SYNC_INTERVAL_TICKS = 20

    private val managed = mutableListOf<KeyMapping>()

    lateinit var openClickGui: KeyMapping
        private set

    private var applied = false
    private var syncCooldown = SYNC_INTERVAL_TICKS

    fun init() {
        openClickGui = register(
            KeyMapping(
                "key.skycore.clickgui",
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

            while (openClickGui.consumeClick()) {
                client.setScreenAndShow(SkyCoreClickGui())
            }
        }
    }

    private fun register(mapping: KeyMapping): KeyMapping {
        val registered = KeyMappingHelper.registerKeyMapping(mapping)
        managed += registered
        return registered
    }

    private fun applyFromConfig() {
        val stored = SkyCoreConfig.instance.keybinds
        if (stored.isEmpty()) {

            captureRebinds()
            return
        }

        var changed = false
        for (mapping in managed) {
            val saved = stored[mapping.name] ?: continue
            val key = runCatching { InputConstants.getKey(saved) }.getOrNull()
            if (key == null) {
                SkyCore.logger.warn("ignoring unparseable keybind '{}' for {}", saved, mapping.name)
                continue
            }
            if (key != KeyMappingHelper.getBoundKeyOf(mapping)) {
                mapping.setKey(key)
                changed = true
            }
        }

        if (changed) KeyMapping.resetMapping()
    }

    fun labelOf(mapping: KeyMapping): String =
        KeyMappingHelper.getBoundKeyOf(mapping).displayName.string

    fun rebind(mapping: KeyMapping, key: InputConstants.Key) {
        mapping.setKey(key)
        KeyMapping.resetMapping()
        captureRebinds()
    }

    private fun captureRebinds() {
        val stored = SkyCoreConfig.instance.keybinds
        var dirty = false

        for (mapping in managed) {
            val current = mapping.saveString()
            if (stored[mapping.name] != current) {
                stored[mapping.name] = current
                dirty = true
            }
        }

        if (dirty) {
            SkyCoreConfig.save()
            SkyCore.logger.debug("keybinds written to config")
        }
    }
}
