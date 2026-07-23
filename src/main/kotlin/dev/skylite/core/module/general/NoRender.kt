package dev.skylite.core.module.general

import com.google.common.collect.Sets
import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.location.IslandType
import dev.skylite.core.location.LocationManager
import dev.skylite.core.skyblock.ItemData
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object NoRender {

    fun init() {}

    private val EXPLOSIONS: Set<ParticleType<*>> = Sets.newHashSet(
        ParticleTypes.EXPLOSION,
        ParticleTypes.EXPLOSION_EMITTER,
        ParticleTypes.GUST,
        ParticleTypes.GUST_EMITTER_LARGE
    )

    private val TREE_BLOCKS = Sets.newHashSet(
        Blocks.MANGROVE_WOOD,
        Blocks.MANGROVE_LEAVES,
        Blocks.STRIPPED_SPRUCE_WOOD,
        Blocks.AZALEA_LEAVES
    )

    fun shouldCancelEntity(entity: Entity): Boolean {
        if (!enabled()) return false
        val cfg = SkyLiteConfig.instance.noRender
        val dungeons = inDungeons()

        if (cfg.deadEntities && entity is LivingEntity && !entity.isAlive) return true
        if (cfg.fallingBlocks && entity is FallingBlockEntity) return true
        if (cfg.lightning && entity is LightningBolt) return true
        if (cfg.expOrbs && entity is ExperienceOrb) return true
        if (cfg.treeBits && entity is Display.BlockDisplay && TREE_BLOCKS.contains(entity.blockState.block)) return true
        if (cfg.guidedSheep && dungeons && entity is Sheep && entity.health == 8.0f) return true
        if (cfg.bonePlating && dungeons && entity is ItemEntity) {
            val stack = entity.item
            if (stack.item == Items.BONE_MEAL && ItemData.plain(stack.hoverName) == "Bone Meal") return true
        }
        return false
    }

    fun shouldCancelParticle(packet: ClientboundLevelParticlesPacket): Boolean {
        if (!enabled()) return false
        val cfg = SkyLiteConfig.instance.noRender
        val type = packet.particle.type

        if (cfg.deadPoof && type == ParticleTypes.POOF && isPoofParticle(packet)) return true
        if (cfg.explosions && EXPLOSIONS.contains(type)) return true
        if (cfg.mageBeam && inDungeons() && type == ParticleTypes.FIREWORK) return true
        if (cfg.iceSpray && matches(packet, ParticleTypes.POOF, 3, 0f, 0f, 0f, 0f)) return true
        if (cfg.powderCoating && isCoatingParticle(packet)) return true
        return false
    }

    fun shouldHideFireOverlay(): Boolean = enabled() && SkyLiteConfig.instance.noRender.fireOverlay

    fun shouldHideBossBar(): Boolean = enabled() && SkyLiteConfig.instance.noRender.bossBar

    fun shouldHideFog(): Boolean = enabled() && SkyLiteConfig.instance.noRender.fog

    fun shouldHideNausea(): Boolean = enabled() && SkyLiteConfig.instance.noRender.nausea

    fun shouldHideBreakParticles(): Boolean = enabled() && SkyLiteConfig.instance.noRender.breakParticles

    fun shouldHideArmorBar(): Boolean = enabled() && SkyLiteConfig.instance.noRender.armorBar

    fun shouldHideFoodBar(): Boolean = enabled() && SkyLiteConfig.instance.noRender.foodBar

    fun shouldHideEffectDisplay(): Boolean = enabled() && SkyLiteConfig.instance.noRender.effectDisplay

    fun shouldHideSelectedItemName(): Boolean = enabled() && SkyLiteConfig.instance.noRender.selectedItemName

    fun shouldHideRecipeBook(): Boolean = enabled() && SkyLiteConfig.instance.noRender.recipeBook

    fun shouldHideEmptyTooltips(slot: Slot?, title: String): Boolean {
        if (title.startsWith("Ultrasequencer (")) return false
        return enabled() &&
            SkyLiteConfig.instance.noRender.emptyTooltips &&
            slot != null &&
            slot.item.hoverName.string.trim().isEmpty()
    }

    fun shouldHideEntityFire(): Boolean = enabled() && SkyLiteConfig.instance.noRender.entityFire

    fun shouldHideSoulweaverSkulls(): Boolean = enabled() && SkyLiteConfig.instance.noRender.soulweaverSkulls

    fun shouldHideHealerFairy(): Boolean = enabled() && SkyLiteConfig.instance.noRender.healerFairy

    fun emptyFog(data: net.minecraft.client.renderer.fog.FogData): net.minecraft.client.renderer.fog.FogData {
        data.renderDistanceStart = Float.MAX_VALUE
        data.renderDistanceEnd = Float.MAX_VALUE
        data.environmentalStart = Float.MAX_VALUE
        data.environmentalEnd = Float.MAX_VALUE
        return data
    }

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.noRender.enabled

    private fun inDungeons(): Boolean =
        LocationManager.current == IslandType.DUNGEONS

    private fun isPoofParticle(packet: ClientboundLevelParticlesPacket): Boolean {
        if (packet.count != 1 || packet.maxSpeed != 0.0f) return false
        for (offset in floatArrayOf(packet.xDist, packet.yDist, packet.zDist)) {
            if (offset >= 0.1f || offset <= -0.1f || offset == 0.0f) return false
        }
        return true
    }

    private fun isCoatingParticle(packet: ClientboundLevelParticlesPacket): Boolean {
        val particle = packet.particle
        if (particle !is DustParticleOptions) return false
        val color = particle.color
        val white = color.x == 1.0f && color.y == 1.0f && color.z == 1.0f
        val orange = color.x == 1.0f && color.y == 0.6f && color.z == 0.0f
        if (!white && !orange) return false
        return matches(packet, ParticleTypes.DUST, 0, 1.0f, 1.0f, 1.0f, 1.0f) ||
            matches(packet, ParticleTypes.DUST, 0, 1.0f, 1.0f, 0.6f, 0.0f)
    }

    private fun matches(
        packet: ClientboundLevelParticlesPacket,
        type: ParticleType<*>,
        count: Int,
        speed: Float,
        ox: Float,
        oy: Float,
        oz: Float
    ): Boolean =
        packet.particle.type == type &&
            packet.count == count &&
            packet.maxSpeed == speed &&
            packet.xDist == ox &&
            packet.yDist == oy &&
            packet.zDist == oz
}
