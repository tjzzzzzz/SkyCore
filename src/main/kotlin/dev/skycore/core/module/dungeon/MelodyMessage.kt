package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object MelodyMessage {

    private val opts get() = SkyCoreConfig.instance.melodyMessage

    private var lastCount = 4

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> lastCount = 4 }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> lastCount = 4 }
    }

    fun onScreenOpen(title: String) {
        if (!active() || !DungeonUtil.onFloor("7") || !isMelody(title)) return
        Titles.sendChatOrCommand(opts.msg)
        lastCount = 4
    }

    fun onScreenClose() {}

    fun onSlotUpdate(title: String, menu: AbstractContainerMenu) {
        if (!active() || !isMelody(title) || !opts.progress || !DungeonUtil.onFloor("7")) return
        val player = Minecraft.getInstance().player ?: return
        val containerSlots = menu.slots.filter { it.container !== player.inventory }
        if (containerSlots.count { !it.item.isEmpty } != 54) return
        var count = 0
        for (slot in containerSlots.asReversed()) {
            val item = slot.item.item
            if (item == Items.DYED_TERRACOTTA.red() || item == Items.DYED_TERRACOTTA.lime()) {
                count++
            }
            if (item == Items.DYED_TERRACOTTA.lime() && count < lastCount) {
                val percent = when (count) {
                    1 -> "75%"
                    2 -> "50%"
                    3 -> "25%"
                    else -> ""
                }
                if (percent.isNotEmpty()) {
                    Titles.sendChatOrCommand(opts.progressMsg.replace("{percent}", percent))
                    lastCount = count
                }
                break
            }
        }
    }

    fun onSlotUpdate(
        title: String,
        stack: ItemStack,
        slotId: Int,
        menu: AbstractContainerMenu,
        isInventory: Boolean
    ) {
        if (isInventory) return
        onSlotUpdate(title, menu)
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    private fun isMelody(title: String): Boolean =
        TerminalSolvers.terminalType(title) == TerminalSolvers.TerminalType.Melody
}
