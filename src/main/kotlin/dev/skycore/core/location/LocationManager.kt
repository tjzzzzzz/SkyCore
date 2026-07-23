package dev.skycore.core.location

import dev.skycore.SkyCore
import dev.skycore.net.SkyCoreHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.azureaaron.hmapi.events.HypixelPacketEvents
import net.azureaaron.hmapi.network.HypixelNetworking
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.scores.DisplaySlot
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LocationManager {

    private const val AREA_ICON = '⏣'

    private const val LOCRAW_DELAY_TICKS = 40

    private const val SIDEBAR_INTERVAL_TICKS = 20

    fun interface IslandChangeListener {
        fun onIslandChange(oldIsland: IslandType, newIsland: IslandType)
    }

    private val listeners = CopyOnWriteArrayList<IslandChangeListener>()
    private val asyncListeners = CopyOnWriteArrayList<suspend (IslandType, IslandType) -> Unit>()

    @Volatile
    var current: IslandType = IslandType.UNKNOWN
        private set

    @Volatile
    var rawMode: String = ""
        private set

    @Volatile
    var slayerActive: Boolean = false
        private set

    @Volatile
    var onSkyblock: Boolean = false
        private set

    private var sidebarCooldown = 0
    private var locrawCountdown = -1

    @Volatile
    private var awaitingLocraw = false

    @Volatile
    var modApiActive: Boolean = false
        private set

    fun init() {
        registerModApi()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            reset()
            locrawCountdown = LOCRAW_DELAY_TICKS
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }

        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay) return@register true
            val raw = message.string
            if (!raw.startsWith("{\"server\":")) return@register true

            handleLocraw(raw)
            val ours = awaitingLocraw
            awaitingLocraw = false
            !ours
        }
    }

    private fun registerModApi() {
        val requested = Object2IntOpenHashMap<CustomPacketPayload.Type<HypixelS2CPacket>>()
        requested.put(LocationUpdateS2CPacket.ID, 1)

        runCatching { HypixelNetworking.registerToEvents(requested) }
            .onFailure {
                SkyCore.logger.warn("mod api registration refused, falling back to /locraw", it)
                return
            }

        HypixelPacketEvents.LOCATION_UPDATE.register { packet -> onLocationPacket(packet) }
    }

    private fun onLocationPacket(packet: HypixelS2CPacket) {
        if (packet !is LocationUpdateS2CPacket) return

        modApiActive = true
        locrawCountdown = -1

        onSkyblock = packet.serverType().orElse("") == "SKYBLOCK"
        val mode = packet.mode().orElse("")
        val island = if (onSkyblock) IslandType.fromApiId(mode) else IslandType.UNKNOWN

        apply(island, mode)
    }

    fun onIslandChange(listener: IslandChangeListener) {
        listeners += listener
    }

    fun onIslandChangeAsync(listener: suspend (oldIsland: IslandType, newIsland: IslandType) -> Unit) {
        asyncListeners += listener
    }

    private fun tick(client: Minecraft) {

        if (locrawCountdown > 0 && --locrawCountdown == 0 && !modApiActive) requestLocraw(client)

        if (--sidebarCooldown > 0) return
        sidebarCooldown = SIDEBAR_INTERVAL_TICKS
        readSidebar(client)
    }

    private fun requestLocraw(client: Minecraft) {
        val connection = client.player?.connection ?: return
        awaitingLocraw = true
        connection.sendCommand("locraw")
    }

    private fun readSidebar(client: Minecraft) {
        val scoreboard = client.level?.scoreboard ?: return
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return

        var areaLine: String? = null
        var sawSlayer = false

        for (holder in scoreboard.trackedPlayers) {
            if (!scoreboard.listPlayerScores(holder).containsKey(objective)) continue
            val team = scoreboard.getPlayersTeam(holder.scoreboardName) ?: continue

            val line = ChatFormatting.stripFormatting(
                team.playerPrefix.string + team.playerSuffix.string
            )?.trim().orEmpty()
            if (line.isEmpty()) continue

            if (line.indexOf(AREA_ICON) >= 0) areaLine = line
            if (line.lowercase(Locale.ENGLISH).contains("slayer quest")) sawSlayer = true
        }

        slayerActive = sawSlayer

        if (modApiActive || current != IslandType.UNKNOWN) return
        val area = areaLine ?: return
        val guess = IslandType.fromSidebarName(area)
        if (guess != IslandType.UNKNOWN) apply(guess, rawMode)
    }

    private fun handleLocraw(json: String) {

        SkyCore.scope.launch {
            val parsed = runCatching {
                SkyCoreHttp.json.decodeFromString<LocrawResponse>(json)
            }.getOrElse {
                SkyCore.logger.warn("could not parse locraw reply", it)
                return@launch
            }

            onSkyblock = parsed.gameType == "SKYBLOCK"
            val island = if (onSkyblock) IslandType.fromApiId(parsed.mode) else IslandType.UNKNOWN

            withContext(Dispatchers.Main.immediate) {
                apply(island, parsed.mode.orEmpty())
            }
        }
    }

    private fun apply(island: IslandType, mode: String) {
        rawMode = mode
        val previous = current
        if (previous == island) return
        current = island

        SkyCore.logger.debug("island {} -> {}", previous, island)
        fire(previous, island)
    }

    private fun fire(oldIsland: IslandType, newIsland: IslandType) {
        val client = Minecraft.getInstance()

        client.execute {
            for (listener in listeners) {
                runCatching { listener.onIslandChange(oldIsland, newIsland) }
                    .onFailure { SkyCore.logger.error("island listener failed", it) }
            }
        }

        for (listener in asyncListeners) {
            SkyCore.scope.launch {
                runCatching { listener(oldIsland, newIsland) }
                    .onFailure { SkyCore.logger.error("async island listener failed", it) }
            }
        }
    }

    private fun reset() {
        val previous = current
        current = IslandType.UNKNOWN
        rawMode = ""
        slayerActive = false
        onSkyblock = false
        awaitingLocraw = false
        locrawCountdown = -1
        if (previous != IslandType.UNKNOWN) fire(previous, IslandType.UNKNOWN)
    }

    @Serializable
    private data class LocrawResponse(
        val server: String = "",
        @SerialName("gametype") val gameType: String? = null,
        val mode: String? = null,
        val map: String? = null
    )
}
