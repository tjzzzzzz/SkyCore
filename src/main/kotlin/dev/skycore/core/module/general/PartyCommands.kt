package dev.skycore.core.module.general

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.skyblock.PartyChat
import dev.skycore.core.skyblock.Titles
import net.minecraft.client.Minecraft

object PartyCommands {

    private val INSTANCES = mapOf(
        "f0" to "CATACOMBS_ENTRANCE",
        "f1" to "CATACOMBS_FLOOR_ONE",
        "f2" to "CATACOMBS_FLOOR_TWO",
        "f3" to "CATACOMBS_FLOOR_THREE",
        "f4" to "CATACOMBS_FLOOR_FOUR",
        "f5" to "CATACOMBS_FLOOR_FIVE",
        "f6" to "CATACOMBS_FLOOR_SIX",
        "f7" to "CATACOMBS_FLOOR_SEVEN",
        "m1" to "MASTER_CATACOMBS_FLOOR_ONE",
        "m2" to "MASTER_CATACOMBS_FLOOR_TWO",
        "m3" to "MASTER_CATACOMBS_FLOOR_THREE",
        "m4" to "MASTER_CATACOMBS_FLOOR_FOUR",
        "m5" to "MASTER_CATACOMBS_FLOOR_FIVE",
        "m6" to "MASTER_CATACOMBS_FLOOR_SIX",
        "m7" to "MASTER_CATACOMBS_FLOOR_SEVEN"
    )

    fun init() {
        PartyChat.onMessage { msg ->
            if (!enabled()) return@onMessage
            val cfg = SkyCoreConfig.instance.partyCommands
            if (cfg.prefixes.isBlank()) return@onMessage
            if ((!cfg.self && msg.self) || isBlacklisted(msg.sender)) return@onMessage
            val lower = msg.content.lowercase().trim()
            val prefixes = cfg.prefixes.lowercase().split(' ').filter { it.isNotEmpty() }
            for (prefix in prefixes) {
                if (!lower.startsWith(prefix)) continue
                val content = lower.removePrefix(prefix).trim()
                if (content.isEmpty()) return@onMessage
                val parts = content.split(' ')
                val name = parts[0]
                val author = msg.sender
                when {
                    cfg.warp && (name == "warp" || name == "w") -> {
                        Titles.sendChatOrCommand("/party warp")
                        return@onMessage
                    }
                    cfg.transfer && (name == "pt" || name == "ptme") -> {
                        Titles.sendChatOrCommand("/party transfer $author")
                        return@onMessage
                    }
                    cfg.allInvite && name == "allinv" -> {
                        Titles.sendChatOrCommand("/party settings allinvite")
                        return@onMessage
                    }
                    cfg.coords && name == "coords" -> {
                        val player = Minecraft.getInstance().player ?: return@onMessage
                        val pos = player.blockPosition()
                        Titles.sendChatOrCommand("/pc x: ${pos.x} y: ${pos.y} z: ${pos.z}")
                        return@onMessage
                    }
                    cfg.kick && (name == "kick" || name == "k") -> {
                        if (parts.size < 2) return@onMessage
                        Titles.sendChatOrCommand("/party kick ${parts[1]}")
                        return@onMessage
                    }
                    cfg.queue && INSTANCES.containsKey(name) -> {
                        Titles.sendChatOrCommand("/joininstance ${INSTANCES[name]}")
                        return@onMessage
                    }
                    cfg.downtime && name == "dt" -> {
                        Titles.show("Downtime", stay = 60)
                        Titles.playPling()
                        Titles.info("Downtime requested by $author")
                        return@onMessage
                    }
                }
            }
        }
    }

    private fun enabled(): Boolean =
        SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.partyCommands.enabled

    private fun isBlacklisted(sender: String): Boolean {
        val cfg = SkyCoreConfig.instance.partyCommands
        return cfg.blacklist.any { it.equals(sender, ignoreCase = true) }
    }

    fun isWhitelisted(sender: String): Boolean {
        val cfg = SkyCoreConfig.instance.partyCommands
        return cfg.whitelist.any { it.equals(sender, ignoreCase = true) }
    }
}
