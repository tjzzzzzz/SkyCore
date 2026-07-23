package dev.skylite.core.module.general

import dev.skylite.config.SkyLiteConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

object Fullbright {

    enum class Mode {
        AMBIENT,
        GAMMA,
        POTION
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!isActive()) return@register
            val player = Minecraft.getInstance().player ?: return@register
            when (mode()) {
                Mode.POTION -> player.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, 840, 0, false, false, false))
                else -> if (noEffect()) player.removeEffect(MobEffects.NIGHT_VISION)
            }
        }
    }

    fun isActive(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.fullbright.enabled

    fun mode(): Mode =
        when (SkyLiteConfig.instance.fullbright.mode.trim().lowercase()) {
            "gamma" -> Mode.GAMMA
            "potion" -> Mode.POTION
            else -> Mode.AMBIENT
        }

    fun noEffect(): Boolean =
        SkyLiteConfig.instance.fullbright.noEffect
}
