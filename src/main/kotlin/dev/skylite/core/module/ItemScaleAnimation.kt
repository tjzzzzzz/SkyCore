package dev.skylite.core.module

import dev.skylite.config.SkyLiteConfig

/**
 * first-person held item scale, position, rotation and swing timing.
 *
 * mirrors the surface of Odin Animations / NoFrills Viewmodel. the actual
 * pose work lives in [dev.skylite.mixin.client.ItemInHandRendererMixin] and
 * swing timing in [dev.skylite.mixin.client.LivingEntityMixin]; this object
 * is the config-facing gate those mixins call into.
 */
object ItemScaleAnimation {

    private val opts get() = SkyLiteConfig.instance.itemScaleAnimation

    @JvmStatic
    fun isActive(): Boolean = opts.enabled

    @JvmStatic
    fun getSize(): Float = opts.size

    @JvmStatic
    fun getX(): Float = opts.x

    @JvmStatic
    fun getY(): Float = opts.y

    @JvmStatic
    fun getZ(): Float = opts.z

    @JvmStatic
    fun getYaw(): Float = opts.yaw

    @JvmStatic
    fun getPitch(): Float = opts.pitch

    @JvmStatic
    fun getRoll(): Float = opts.roll

    @JvmStatic
    fun getIgnoreEffects(): Boolean = opts.ignoreEffects

    @JvmStatic
    fun getSpeed(): Int = opts.speed.toInt().coerceAtLeast(1)

    @JvmStatic
    fun shouldStopSwing(): Boolean = opts.enabled && opts.noSwing

    @JvmStatic
    fun shouldSkipEquipReset(): Boolean = opts.enabled && opts.noEquipReset

    @JvmStatic
    fun shouldNotReSwing(): Boolean = opts.enabled && opts.disableReSwing
}
