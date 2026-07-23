package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

object PrinceMessage {

    private const val CHAT = "A Prince falls. +1 Bonus Score"

    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !DungeonUtil.inDungeons()) return@register
            if (ItemData.plain(message).trim() != CHAT) return@register
            if (SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.princeMessage.enabled) {
                Titles.sendChatOrCommand(SkyCoreConfig.instance.princeMessage.message)
            }
            if (SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.scoreCalculator.enabled) {
                ScoreCalculator.setPrinceKilled()
            }
        }
    }
}
