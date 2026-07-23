package dev.skylite.core.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import dev.skylite.ui.theme.Theme

object Titles {

    private val PREFIX: Component = Component.empty()
        .append(Component.literal("[").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7C8C9E))))
        .append(Component.literal("SkyLite").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Theme.ACCENT and 0xFFFFFF))))
        .append(Component.literal("] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7C8C9E))))

    fun prefix(): Component = PREFIX.copy()

    fun tagged(message: String, color: Int = 0xF0F4F8): MutableComponent =
        PREFIX.copy().append(Component.literal(message).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color and 0xFFFFFF))))

    fun show(title: String, subtitle: String = "", fadeIn: Int = 5, stay: Int = 40, fadeOut: Int = 10) {
        val hud = Minecraft.getInstance().gui.hud
        hud.setTimes(fadeIn, stay, fadeOut)
        hud.setTitle(Component.literal(title))
        if (subtitle.isNotEmpty()) {
            hud.setSubtitle(Component.literal(subtitle))
        }
    }

    fun play(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, pitch, volume))
    }

    fun playOrb() = play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0f)

    fun playPling() = play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)

    fun playBass() = play(SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0f)

    fun playHarp() = play(SoundEvents.NOTE_BLOCK_HARP.value(), 1f, 0f)

    fun sendChatOrCommand(message: String) {
        if (message.isEmpty()) return
        val player = Minecraft.getInstance().player ?: return
        if (message.startsWith("/")) {
            player.connection.sendCommand(message.substring(1))
        } else {
            player.connection.sendChat(message)
        }
    }

    fun info(message: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(tagged(stripLegacyPrefix(message)))
    }

    fun success(message: String) = sendColored(message, Theme.SUCCESS and 0xFFFFFF)

    fun warn(message: String) = sendColored(message, Theme.WARNING and 0xFFFFFF)

    fun error(message: String) = sendColored(message, Theme.DANGER and 0xFFFFFF)

    private fun sendColored(message: String, color: Int) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(tagged(stripLegacyPrefix(message), color))
    }

    private fun stripLegacyPrefix(message: String): String =
        message.replace(Regex("^§[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
}
