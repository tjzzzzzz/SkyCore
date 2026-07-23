package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.ItemData
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object DungeonChestValue {

    private val opts get() = SkyCoreConfig.instance.dungeonChestValue

    @Volatile
    private var currentValue = 0.0

    fun init() {
        DungeonPricing.init()
    }

    fun isChest(title: String): Boolean {
        for (name in DungeonUtil.chestNames()) {
            if (title == name || (title.startsWith(name) && title.endsWith("Chest"))) {
                return true
            }
        }
        return false
    }

    fun onScreenOpen() {
        currentValue = 0.0
    }

    fun onScreenOpen(title: String) {
        onScreenOpen()
    }

    fun onScreenClose() {
        currentValue = 0.0
    }

    fun onSlotUpdate(title: String, stack: ItemStack, isInventory: Boolean) {
        if (!active() || !isChest(title) || !DungeonPricing.inLootArea()) return
        if (isInventory || stack.item == Items.STAINED_GLASS_PANE.black()) return
        val name = ItemData.plain(stack.hoverName)
        val id = DungeonPricing.lootId(stack, name)
        if (id.isEmpty()) {
            if (name == "Open Reward Chest") {
                val cost = ItemData.loreLines(stack).firstOrNull { it.endsWith(" Coins") }
                if (cost != null) {
                    val value = cost.removeSuffix(" Coins").replace(",", "").toIntOrNull() ?: 0
                    currentValue -= value
                }
            }
            return
        }
        currentValue += DungeonPricing.lootValue(id) * DungeonPricing.lootQuantity(stack, name)
    }

    fun onSlotUpdate(title: String, slotId: Int, stack: ItemStack, isContainerSlot: Boolean) {
        onSlotUpdate(title, stack, isInventory = !isContainerSlot)
    }

    fun onScreenRender(context: GuiGraphicsExtractor, title: String, menu: AbstractContainerMenu) {
        if (!active() || currentValue == 0.0 || !isChest(title)) return
        val client = Minecraft.getInstance()
        val font = client.font
        val slot = menu.getSlot(4)
        val value = "Chest Value: ${DungeonPricing.formatSeparator(currentValue)}"
        val width = font.width(value)
        val baseX = slot.x + 8
        val baseY = slot.y + 8
        context.fill(
            kotlin.math.floor(baseX - 2 - width * 0.5).toInt(),
            baseY - 6,
            kotlin.math.ceil(baseX + 2 + width * 0.5).toInt(),
            baseY + 6,
            opts.background
        )
        val color = if (currentValue > 0) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
        context.text(font, value, baseX - width / 2, baseY - 4, color, false)
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled
}
