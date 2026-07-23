package dev.skylite.core.module.mining

import dev.skylite.config.SkyLiteConfig
import dev.skylite.mixin.client.ItemInHandRendererAccessor
import dev.skylite.mixin.client.MultiPlayerGameModeAccessor
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object BreakResetFix {

    fun init() {}

    fun onInventory(slotId: Int, stack: ItemStack) {
        if (!SkyLiteConfig.instance.enabled || !SkyLiteConfig.instance.breakResetFix.enabled) return
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
