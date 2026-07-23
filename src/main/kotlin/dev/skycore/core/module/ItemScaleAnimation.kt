package dev.skycore.core.module

import dev.skycore.config.SkyCoreConfig

object ItemScaleAnimation {

    private val opts get() = SkyCoreConfig.instance.itemScaleAnimation

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
