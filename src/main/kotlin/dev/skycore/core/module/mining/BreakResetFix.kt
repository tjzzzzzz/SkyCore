package dev.skycore.core.module.mining

import dev.skycore.config.SkyCoreConfig
import dev.skycore.mixin.client.ItemInHandRendererAccessor
import dev.skycore.mixin.client.MultiPlayerGameModeAccessor
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object BreakResetFix {

    fun init() {}

    fun onInventory(slotId: Int, stack: ItemStack) {
        if (!SkyCoreConfig.instance.enabled || !SkyCoreConfig.instance.breakResetFix.enabled) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val gameMode = client.gameMode ?: return
        if (slotId !in 36..44) return
        if (player.inventory.selectedSlot != slotId - 36) return
        (gameMode as MultiPlayerGameModeAccessor).setDestroyingItem(stack)
        val handRenderer = client.entityRenderDispatcher.itemInHandRenderer
        (handRenderer as ItemInHandRendererAccessor).setMainHandItem(stack)
    }
}
