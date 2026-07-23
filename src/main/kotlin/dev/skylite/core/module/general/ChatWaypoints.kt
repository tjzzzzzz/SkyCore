package dev.skylite.core.module.general

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.render.WorldBoxes
import dev.skylite.core.skyblock.ItemData
import dev.skylite.core.skyblock.PartyChat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import java.util.concurrent.CopyOnWriteArrayList

object ChatWaypoints {

    private const val PARTY_COLOR = 0xAA5555FF.toInt()
    private const val ALL_COLOR = 0xAA55FFFF.toInt()

    private val waypoints = CopyOnWriteArrayList<Waypoint>()

    fun init() {
        PartyChat.onMessage { msg ->
            if (!enabled() || !SkyLiteConfig.instance.chatWaypoints.partyEnabled || msg.self) return@onMessage
            parseCoords(msg.content, msg.sender, party = true)
        }

        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !enabled() || !SkyLiteConfig.instance.chatWaypoints.allEnabled) return@register
            val plain = ItemData.plain(message)
            if (plain.startsWith("[NPC]") || plain.startsWith("[BOSS]") ||
                plain.startsWith("Guild > ") || plain.startsWith("Party > ")
            ) return@register
            val split = plain.indexOf(':')
            if (split < 0) return@register
            val senderInfo = plain.substring(0, split).trim()
            val sender = if (' ' in senderInfo) senderInfo.substringAfterLast(' ').trim() else senderInfo
            if (!isPlayerValid(sender)) return@register
            parseCoords(plain.substring(split + 1), sender, party = false)
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled() || waypoints.isEmpty()) return@register
            for (wp in waypoints) {
                if (wp.duration > 0) wp.duration--
            }
            waypoints.removeIf { it.duration <= 0 }
        }

        WorldBoxes.onRender { _, _ ->
            if (!enabled() || waypoints.isEmpty()) return@onRender
            val player = Minecraft.getInstance().player ?: return@onRender
            waypoints.removeIf { wp ->
                wp.shouldClear() && wp.box.center.distanceTo(player.position()) <= 8.0
            }
            for (wp in waypoints) {
                val color = if (wp.party) PARTY_COLOR else ALL_COLOR
                val outline = color or 0xFF000000.toInt()
                WorldBoxes.both(wp.box, color, outline)
                val beam = AABB(
                    wp.box.minX, wp.box.maxY, wp.box.minZ,
                    wp.box.maxX, wp.box.maxY + 256.0, wp.box.maxZ
                )
                WorldBoxes.filled(beam, (color and 0x00FFFFFF) or 0x55000000)
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> waypoints.clear() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> waypoints.clear() }
    }

    private fun enabled(): Boolean =
        SkyLiteConfig.instance.enabled && SkyLiteConfig.instance.chatWaypoints.enabled

    private fun isPlayerValid(name: String): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (player.gameProfile.name.equals(name, ignoreCase = true)) return false
        val connection = Minecraft.getInstance().connection ?: return false
        return connection.getPlayerInfo(name) != null
    }

    private fun parseCoords(message: String, sender: String, party: Boolean) {
        val coords = ArrayList<Double>(3)
        var skipNextError = false
        for (raw in message.split(' ')) {
            var token = raw
            if (token.endsWith(',') && token.indexOf(',') == token.lastIndexOf(',')) {
                token = token.dropLast(1)
                skipNextError = true
            }
            val parsed = token.toDoubleOrNull()
            if (parsed != null) {
                coords += parsed
            } else {
                if (!skipNextError) coords.clear()
                skipNextError = false
            }
            if (coords.size == 3) {
                val pos = BlockPos(
                    kotlin.math.floor(coords[0]).toInt(),
                    kotlin.math.floor(coords[1]).toInt(),
                    kotlin.math.floor(coords[2]).toInt()
                )
                if (pos.y < 0 || pos.y > 256) break
                val cfg = SkyLiteConfig.instance.chatWaypoints
                val duration = (if (party) cfg.partyDuration else cfg.allDuration) * 20
                waypoints.removeIf { it.sender.equals(sender, ignoreCase = true) }
                waypoints += Waypoint(sender, AABB.encapsulatingFullBlocks(pos, pos), duration, party)
                break
            }
        }
    }

    private class Waypoint(
        val sender: String,
        val box: AABB,
        var duration: Int,
        val party: Boolean
    ) {
        fun shouldClear(): Boolean {
            val cfg = SkyLiteConfig.instance.chatWaypoints
            return if (party) cfg.partyClearOnArrive else cfg.allClearOnArrive
        }
    }
}
