package dev.skylite.core.module.general

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.skyblock.ItemData
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

object ItemProtection {

    fun init() {}

    var valueResolver: (ItemStack) -> Double = { 0.0 }

    private var sellGui = false
    private var salvageGui = false

    fun isProtected(stack: ItemStack): Boolean {
        if (!enabled() || stack.isEmpty || isOverrideActive()) return false
        val cfg = SkyLiteConfig.instance.itemProtection
        val data = ItemData.customData(stack)
        val id = ItemData.skyblockId(stack)

        if (cfg.uuidList.isNotEmpty()) {
            val uuid = data.getStringOr("uuid", "")
            if (uuid.isNotEmpty() && cfg.uuidList.any { it.equals(uuid, ignoreCase = true) }) return true
        }
        if (cfg.idList.isNotEmpty() && id.isNotEmpty() &&
            cfg.idList.any { it.equals(id, ignoreCase = true) }
        ) return true
        if (cfg.protectMaxQuality && data.getIntOr("baseStatBoostPercentage", 0) == 50) return true
        if (cfg.protectStarred && data.getIntOr("upgrade_level", 0) > 0 && !data.contains("boss_tier")) return true
        if (cfg.protectRecomb && data.getIntOr("rarity_upgrades", 0) > 0) return true
        if (cfg.protectValue && valueResolver(stack) >= cfg.protectValueMin) return true
        return false
    }

    fun shouldBlockDrop(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return isProtected(player.mainHandItem)
    }

    fun isOverrideActive(): Boolean {
        val client = Minecraft.getInstance()
        return GLFW.glfwGetKey(client.window.handle(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
    }

    fun onScreenOpen(title: String) {
        sellGui = false
        salvageGui = title.equals("Salvage Items", ignoreCase = true)
    }

    fun onScreenClose() {
        sellGui = false
        salvageGui = false
    }

    fun onSlotUpdate(stack: ItemStack, playerInventory: Boolean) {
        if (!enabled() || playerInventory || stack.isEmpty) return
        if (isSellStack(stack)) sellGui = true
    }

    fun shouldCancelClick(stack: ItemStack, screenTitle: String, throwing: Boolean): Boolean {
        if (!enabled() || isOverrideActive()) return false
        if (throwing || sellGui || Minecraft.getInstance().gui.screen() == null) {
            return isProtected(stack)
        }
        return false
    }

    fun shouldCancelSalvage(containerStacks: Iterable<ItemStack>): Boolean {
        if (!enabled() || !salvageGui || isOverrideActive()) return false
        return containerStacks.any { isProtected(it) }
    }

    fun isSellGui(): Boolean = sellGui

    fun isSalvageGui(): Boolean = salvageGui

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.itemProtection.enabled

    private fun isSellStack(stack: ItemStack): Boolean {
        val name = ItemData.plain(stack.hoverName)
        if (stack.item == Items.HOPPER && name == "Sell Item") return true
        return ItemData.loreLines(stack).any { it.contains("Click to buyback!") }
    }

    private fun isSalvageButton(stack: ItemStack): Boolean {
        val name = ItemData.plain(stack.hoverName)
        return name == "Salvage Items" || name == "Confirm Salvage"
    }
}
