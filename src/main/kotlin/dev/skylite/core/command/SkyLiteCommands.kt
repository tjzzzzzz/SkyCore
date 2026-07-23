package dev.skylite.core.command

import dev.skylite.ui.clickgui.SkyLiteClickGui
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft

/**
 * client chat commands. /skylite is the always-on way into the click gui so the
 * open keybind can stay unbound by default.
 */
object SkyLiteCommands {

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skylite").executes {
                    val client = Minecraft.getInstance()
                    client.execute {
                        client.setScreenAndShow(SkyLiteClickGui())
                    }
                    1
                }
            )
        }
    }
}
