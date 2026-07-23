package dev.skycore.core.module.dungeon

import com.mojang.authlib.properties.Property
import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.ItemStack
import java.util.Base64

object MimicMessage {

    private const val MIMIC_HASH = "e19c12543bc7792605ef68e1f8749ae8f2a381d9085d4d4b780ba1282d3597a0"

    private val cache = EntityCache.create()

    @Volatile
    private var mimicKilled = false

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> mimicKilled = false }
        DungeonEvents.onUpdated { entity ->
            if (mimicKilled || entity !is Zombie || !entity.isBaby || !DungeonUtil.inDungeons()) return@onUpdated
            if (hasMimicTexture(entity.getItemBySlot(EquipmentSlot.HEAD))) {
                cache.add(entity)
            }
            if (entity.isDeadOrDying && cache.has(entity)) {
                processDeath()
            }
        }
        DungeonEvents.onRemoved { entity ->
            if (mimicKilled || entity !is Zombie || !entity.isDeadOrDying || !cache.has(entity)) return@onRemoved
            processDeath()
        }
    }

    private fun processDeath() {
        if (SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.mimicMessage.enabled) {
            Titles.sendChatOrCommand(SkyCoreConfig.instance.mimicMessage.message)
        }
        if (SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.scoreCalculator.enabled) {
            ScoreCalculator.setMimicKilled()
        }
        mimicKilled = true
    }

    private fun hasMimicTexture(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val profile = stack.get(DataComponents.PROFILE)?.partialProfile() ?: return false
        for (property in profile.properties().values()) {
            if (property !is Property || property.name() != "textures") continue
            val raw = property.value()
            if (raw.contains(MIMIC_HASH)) return true
            try {
                val decoded = String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
                if (decoded.contains(MIMIC_HASH)) return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
