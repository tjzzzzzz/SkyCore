package dev.skylite.core.module.general

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.skyblock.Titles
import dev.skylite.mixin.client.ChatComponentAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth

object ChatTweaks {

    private val FORMATTING = Regex("(?i)§[0-9A-FK-OR]")

    private val opts get() = SkyLiteConfig.instance.chatTweaks

    fun init() {}

    fun isActive(): Boolean =
        SkyLiteConfig.instance.enabled && opts.enabled

    fun lineLimit(): Int = opts.lineLimit.coerceIn(100, 5000)

    fun keepHistory(): Boolean = isActive() && opts.keepHistory

    fun extraLines(): Boolean = isActive() && opts.extraLines

    fun tryCopyAt(mouseX: Double, mouseY: Double): Boolean {
        if (!isActive() || !opts.copyOnRightClick) return false
        val client = Minecraft.getInstance()
        if (client.gui.screen() !is ChatScreen) return false
        val message = hoveredMessage(mouseX.toFloat(), mouseY.toFloat())
        if (message.isEmpty()) return false
        val out = if (opts.trimOnCopy) message.trim() else message
        client.keyboardHandler.setClipboard(out)
        if (opts.msgOnCopy) {
            val previewLen = opts.copyPreviewLength.coerceAtLeast(0)
            if (previewLen == 0) {
                Titles.info("Copied message to clipboard.")
            } else {
                val preview = if (out.length > previewLen) out.take(previewLen) + "..." else out
                Titles.info("Copied \"$preview\"")
            }
        }
        return true
    }

    private fun hoveredMessage(mouseX: Float, mouseY: Float): String {
        val client = Minecraft.getInstance()
        val chat = client.gui.hud.chat
        val accessor = chat as ChatComponentAccessor
        val trimmed = accessor.trimmedMessages
        if (trimmed.isEmpty()) return ""
        val chatBottom = Mth.floor(client.window.guiScaledHeight - 40.0)
        val chatScale = client.options.chatScale().get()
        val entryHeight = (9.0 * (client.options.chatLineSpacing().get() + 1.0)).toInt()
        val chatHeight = ChatComponent.getHeight(client.options.chatHeightFocused().get())
        val scroll = accessor.chatScrollbarPos
        val visibleEnd = minOf(trimmed.size, scroll + chatHeight / entryHeight)
        if (scroll >= visibleEnd) return ""
        val visible = trimmed.subList(scroll, visibleEnd)
        for (index in visible.indices) {
            val entryBottom = (chatBottom - index * (entryHeight * chatScale)).toInt()
            val entryTop = (entryBottom - (entryHeight * chatScale)).toInt()
            val width = ChatComponent.getWidth(client.options.chatWidth().get())
            if (ActiveTextCollector.isPointInRectangle(
                    mouseX, mouseY,
                    0f, entryTop.toFloat(),
                    width.toFloat(), entryBottom.toFloat()
                )
            ) {
                return fullMessage(visible, index).joinToString("") { plain(it.content()) }
            }
        }
        return ""
    }

    private fun fullMessage(visible: List<GuiMessage.Line>, index: Int): List<GuiMessage.Line> {
        val lines = ArrayList<GuiMessage.Line>()
        for (i in index + 1 until visible.size) {
            val line = visible[i]
            if (line.endOfEntry()) break
            lines.add(0, line)
        }
        for (i in index downTo 0) {
            val line = visible[i]
            lines.add(line)
            if (line.endOfEntry()) break
        }
        return lines
    }

    private fun plain(seq: FormattedCharSequence): String {
        val sb = StringBuilder()
        seq.accept { _, _, codepoint ->
            sb.appendCodePoint(codepoint)
            true
        }
        return FORMATTING.replace(sb.toString(), "")
    }
}
