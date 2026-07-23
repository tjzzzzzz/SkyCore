package dev.skycore.core.command

import dev.skycore.ui.clickgui.SkyCoreClickGui
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft

object SkyCoreCommands {

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skycore").executes {
                    val client = Minecraft.getInstance()
                    client.execute {
                        client.setScreenAndShow(SkyCoreClickGui())
                    }
                    1
                }
            )
        }
    }
}
