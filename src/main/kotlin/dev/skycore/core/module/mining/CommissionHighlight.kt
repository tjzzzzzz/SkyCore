package dev.skycore.core.module.mining

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.skyblock.ItemData
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.Items

object CommissionHighlight {

    private const val COLOR = 0x8055FF55.toInt()

    fun init() {}

    fun onScreenRender(context: GuiGraphicsExtractor, title: String, menu: AbstractContainerMenu) {
        if (!SkyCoreConfig.instance.enabled || !SkyCoreConfig.instance.commissionHighlight.enabled) return
        if (title != "Commissions") return
        for (slot in menu.slots) {
            val stack = slot.item
            if (stack.isEmpty || stack.item != Items.WRITABLE_BOOK) continue
            if (ItemData.skyblockId(stack).isNotEmpty()) continue
            if (ItemData.loreLines(stack).any { it == "COMPLETED" }) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, COLOR)
            }
        }
    }
}
