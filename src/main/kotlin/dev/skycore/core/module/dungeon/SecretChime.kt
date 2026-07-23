package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.skyblock.ItemData
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

object SecretChime {

    private val secretItems = setOf(
        "ARCHITECT_FIRST_DRAFT",
        "DUNGEON_DECOY",
        "DUNGEON_CHEST_KEY",
        "INFLATABLE_JERRY",
        "SPIRIT_LEAP",
        "DUNGEON_TRAP",
        "CANDYCOMB",
        "HEALING_8_POTION",
        "TRAINING_WEIGHTS",
        "DEFUSE_KIT",
        "TREASURE_TALISMAN",
        "REVIVE_STONE"
    )

    private val essenceUuid = uuidFromInts(-520885975, -2036449846, -1794878266, 1726902051)

    private val entityCache = EntityCache.create()

    @Volatile
    private var clickedThisTick = false

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> clickedThisTick = false }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (clickedThisTick) clickedThisTick = false
        }
        UseBlockCallback.EVENT.register { _, world, _, hit ->
            if (!world.isClientSide || !active() || clickedThisTick) return@register InteractionResult.PASS
            val level = Minecraft.getInstance().level ?: return@register InteractionResult.PASS
            val pos = hit.blockPos
            val state = level.getBlockState(pos)
            val cfg = SkyCoreConfig.instance.secretChime
            clickedThisTick = true
            if (state.block is ChestBlock && cfg.chestToggle) {
                play(cfg.chestSound, cfg.chestVolume, cfg.chestPitch)
            }
            if (state.block is LeverBlock && cfg.leverToggle) {
                play(cfg.leverSound, cfg.leverVolume, cfg.leverPitch)
            }
            if (isEssence(state, pos) && cfg.essenceToggle) {
                play(cfg.essenceSound, cfg.essenceVolume, cfg.essencePitch)
            }
            InteractionResult.PASS
        }
        DungeonEvents.onUpdated { entity ->
            if (!active()) return@onUpdated
            val cfg = SkyCoreConfig.instance.secretChime
            if (cfg.itemsToggle && entity is ItemEntity) {
                if (marketId(entity.item) in secretItems) entityCache.add(entity)
                if (!entity.isAlive && entityCache.has(entity)) playItemChime(entity)
            }
            if (cfg.batToggle && entity is Bat) {
                if (DungeonUtil.isSecretBat(entity)) entityCache.add(entity)
                if (!entity.isAlive && entityCache.has(entity)) playBatChime(entity)
            }
        }
        DungeonEvents.onRemoved { entity ->
            if (!active()) return@onRemoved
            val cfg = SkyCoreConfig.instance.secretChime
            if (cfg.itemsToggle && entity is ItemEntity && entityCache.has(entity)) {
                playItemChime(entity)
            }
            if (cfg.batToggle && entity is Bat && entityCache.has(entity)) {
                playBatChime(entity)
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.secretChime.enabled &&
            DungeonUtil.inDungeons()

    private fun play(sound: String, volume: Double, pitch: Double) {
        val event = SoundEvent.createVariableRangeEvent(Identifier.parse(sound))
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, pitch.toFloat(), volume.toFloat()))
    }

    private fun playItemChime(entity: Entity) {
        val player = Minecraft.getInstance().player ?: return
        if (entity.distanceTo(player) > 8.0) return
        val cfg = SkyCoreConfig.instance.secretChime
        play(cfg.itemsSound, cfg.itemsVolume, cfg.itemsPitch)
        entityCache.remove(entity)
    }

    private fun playBatChime(entity: Entity) {
        val player = Minecraft.getInstance().player ?: return
        if (entity.distanceTo(player) > 10.0) return
        val cfg = SkyCoreConfig.instance.secretChime
        play(cfg.batSound, cfg.batVolume, cfg.batPitch)
        entityCache.remove(entity)
    }

    private fun isEssence(state: BlockState, pos: BlockPos): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val skull = level.getBlockEntity(pos) as? SkullBlockEntity ?: return false
        val uuid = skullUuid(skull) ?: return false
        return uuid == essenceUuid
    }

    private fun skullUuid(skull: SkullBlockEntity): UUID? {
        try {
            val profileField = SkullBlockEntity::class.java.methods.firstOrNull {
                it.name == "getOwnerProfile" || it.name == "ownerProfile" || it.name == "getOwner"
            }
            val profile = profileField?.invoke(skull) ?: return null
            val partial = profile.javaClass.methods.firstOrNull { it.name == "partialProfile" || it.name == "gameProfile" }
                ?.invoke(profile) ?: profile
            val idMethod = partial.javaClass.methods.firstOrNull { it.name == "id" || it.name == "getId" } ?: return null
            val id = idMethod.invoke(partial) ?: return null
            return when (id) {
                is UUID -> id
                is java.util.Optional<*> -> id.orElse(null) as? UUID
                else -> null
            }
        } catch (_: Throwable) {
            return null
        }
    }

    private fun uuidFromInts(a: Int, b: Int, c: Int, d: Int): UUID {
        val msb = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
        val lsb = (c.toLong() shl 32) or (d.toLong() and 0xffffffffL)
        return UUID(msb, lsb)
    }

    private fun marketId(stack: ItemStack): String {
        val id = ItemData.skyblockId(stack)
        if (id == "POTION") {
            val data = ItemData.customData(stack)
            val potion = data.getStringOr("potion", "")
            if (potion.isNotEmpty()) {
                return "${potion.uppercase()}_${data.getIntOr("potion_level", 0)}_POTION"
            }
        }
        return id
    }
}
