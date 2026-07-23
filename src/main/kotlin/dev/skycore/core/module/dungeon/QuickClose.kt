package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object QuickClose {

    private val opts get() = SkyCoreConfig.instance.quickClose

    @Volatile
    private var closedThisOpen = false

    fun init() {}

    fun tick(screen: Screen, title: String) {
        if (!active() || !DungeonUtil.inDungeons() || !isChest(title)) {
            closedThisOpen = false
            return
        }
        if (closedThisOpen) return
        val options = Minecraft.getInstance().options
        if (
            options.keyUp.isDown ||
            options.keyLeft.isDown ||
            options.keyDown.isDown ||
            options.keyRight.isDown
        ) {
            closedThisOpen = true
            screen.onClose()
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    private fun isChest(title: String): Boolean =
        title.endsWith("Chest") || DungeonChestValue.isChest(title)
}
