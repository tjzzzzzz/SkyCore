package dev.skylite.core.skyblock

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft

object PartyChat {

    data class Message(val sender: String, val content: String, val self: Boolean)

    private val listeners = ArrayList<(Message) -> Unit>()

    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) return@register
            val plain = ItemData.plain(message)
            if (!plain.startsWith("Party > ")) return@register
            val body = plain.removePrefix("Party > ")
            val split = body.indexOf(": ")
            if (split < 0) return@register
            val senderInfo = body.substring(0, split).trim()
            val sender = senderInfo.substringAfterLast(' ').trim()
            val content = body.substring(split + 2)
            val self = Minecraft.getInstance().player?.gameProfile?.name.equals(sender, ignoreCase = true)
            val event = Message(sender, content, self)
            for (listener in listeners) listener(event)
        }
    }

    fun onMessage(listener: (Message) -> Unit) {
        listeners += listener
    }
}
