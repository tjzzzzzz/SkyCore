package dev.skylite.core.module.general

import com.mojang.brigadier.arguments.StringArgumentType
import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.skyblock.Titles
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands

object CommandShortcuts {

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            if (!SkyLiteConfig.instance.commandShortcuts.enabled) return@register
            for (entry in SkyLiteConfig.instance.commandShortcuts.shortcuts) {
                val raw = entry.shortcut.trim()
                if (raw.isEmpty()) continue
                val name = if (raw.startsWith("/")) raw.substring(1).trim() else raw
                if (name.isEmpty()) continue
                val message = entry.message
                dispatcher.register(
                    ClientCommands.literal(name)
                        .executes {
                            Titles.sendChatOrCommand(message)
                            1
                        }
                        .then(
                            ClientCommands.argument("param", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val param = StringArgumentType.getString(ctx, "param").trim()
                                    Titles.sendChatOrCommand("$message $param")
                                    1
                                }
                        )
                )
            }
        }
    }

    fun reload() {
        Titles.info("Reconnect to apply command shortcut changes.")
    }

    fun shortcuts(): MutableList<SkyLiteConfig.CommandShortcutEntry> =
        SkyLiteConfig.instance.commandShortcuts.shortcuts
}
