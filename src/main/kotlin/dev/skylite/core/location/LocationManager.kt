package dev.skylite.core.location

import dev.skylite.SkyLite
import dev.skylite.net.SkyLiteHttp
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

/**
 * tracks which skyblock island the player is on.
 *
 * three sources feed this, in priority order:
 *  1. the hypixel mod api, which pushes a location packet on every server swap.
 *     no command spam, no chat parsing, arrives before the world finishes
 *     loading. this is the one that should normally be doing the work.
 *  2. /locraw, only sent if the mod api has not answered by the time the join
 *     delay elapses. hypixel can disable the mod api server side, so this stays
 *     as a safety net rather than being deleted.
 *  3. the sidebar area line, a last resort while both of the above are silent.
 *
 * threading: the sidebar is client state, so it is only ever read on the client
 * thread during tick. the locraw json is parsed off thread. listeners registered
 * with [onIslandChange] are dispatched back on the client thread because almost
 * anything useful they do (spawning huds, touching the world, opening screens)
 * is not thread safe. use [onIslandChangeAsync] for listeners that do io.
 */
object LocationManager {

    /** sidebar lines are prefixed with this icon on every non rift island */
    private const val AREA_ICON = '⏣'

    /** hypixel needs a moment after world join before it answers /locraw */
    private const val LOCRAW_DELAY_TICKS = 40

    /** re-reading the sidebar every tick is wasted work, once a second is plenty */
    private const val SIDEBAR_INTERVAL_TICKS = 20

    fun interface IslandChangeListener {
        fun onIslandChange(oldIsland: IslandType, newIsland: IslandType)
    }

    private val listeners = CopyOnWriteArrayList<IslandChangeListener>()
    private val asyncListeners = CopyOnWriteArrayList<suspend (IslandType, IslandType) -> Unit>()

    @Volatile
    var current: IslandType = IslandType.UNKNOWN
        private set

    /** raw /locraw mode string, useful for islands the enum does not model yet */
    @Volatile
    var rawMode: String = ""
        private set

    /** true while a slayer quest is on the sidebar, independent of [current] */
    @Volatile
    var slayerActive: Boolean = false
        private set

    @Volatile
    var onSkyblock: Boolean = false
        private set

    private var sidebarCooldown = 0
    private var locrawCountdown = -1

    /** set while we are waiting on our own /locraw so we can hide the reply */
    @Volatile
    private var awaitingLocraw = false

    /** flips once the mod api answers, which retires the /locraw path for good */
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

        // locraw comes back as a normal chat message, swallow it if we asked for it
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

    /**
     * subscribes to the mod api location event. hm-api sends the registration
     * packet itself once hypixel says hello, so doing this in init is enough,
     * there is nothing to redo per join.
     */
    private fun registerModApi() {
        val requested = Object2IntOpenHashMap<CustomPacketPayload.Type<HypixelS2CPacket>>()
        requested.put(LocationUpdateS2CPacket.ID, 1)

        runCatching { HypixelNetworking.registerToEvents(requested) }
            .onFailure {
                SkyLite.logger.warn("mod api registration refused, falling back to /locraw", it)
                return
            }

        HypixelPacketEvents.LOCATION_UPDATE.register { packet -> onLocationPacket(packet) }
    }

    /**
     * the event fires for errors too, so anything that is not a location packet
     * just means the mod api could not answer and the fallbacks stay live.
     */
    private fun onLocationPacket(packet: HypixelS2CPacket) {
        if (packet !is LocationUpdateS2CPacket) return

        modApiActive = true
        locrawCountdown = -1

        onSkyblock = packet.serverType().orElse("") == "SKYBLOCK"
        val mode = packet.mode().orElse("")
        val island = if (onSkyblock) IslandType.fromApiId(mode) else IslandType.UNKNOWN

        // no json to decode here, this is already parsed by the time we see it
        apply(island, mode)
    }

    fun onIslandChange(listener: IslandChangeListener) {
        listeners += listener
    }

    /** for listeners that hit the network or disk, runs off the client thread */
    fun onIslandChangeAsync(listener: suspend (oldIsland: IslandType, newIsland: IslandType) -> Unit) {
        asyncListeners += listener
    }

    private fun tick(client: Minecraft) {
        // only bother the server if the mod api never showed up
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

    /**
     * reads the sidebar objective. cheap, but it has to happen on the client
     * thread since the netty thread rewrites the scoreboard under us.
     */
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

        // a real source already told us where we are, do not let a reworded
        // sidebar line downgrade a known island to UNKNOWN
        if (modApiActive || current != IslandType.UNKNOWN) return
        val area = areaLine ?: return
        val guess = IslandType.fromSidebarName(area)
        if (guess != IslandType.UNKNOWN) apply(guess, rawMode)
    }

    private fun handleLocraw(json: String) {
        // json parsing is the only genuinely expensive bit here, keep it off tick
        SkyLite.scope.launch {
            val parsed = runCatching {
                SkyLiteHttp.json.decodeFromString<LocrawResponse>(json)
            }.getOrElse {
                SkyLite.logger.warn("could not parse locraw reply", it)
                return@launch
            }

            onSkyblock = parsed.gameType == "SKYBLOCK"
            val island = if (onSkyblock) IslandType.fromApiId(parsed.mode) else IslandType.UNKNOWN

            // back to the client thread before anything observes the change
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

        SkyLite.logger.debug("island {} -> {}", previous, island)
        fire(previous, island)
    }

    private fun fire(oldIsland: IslandType, newIsland: IslandType) {
        val client = Minecraft.getInstance()

        // a listener that throws must not take the rest of them down with it
        client.execute {
            for (listener in listeners) {
                runCatching { listener.onIslandChange(oldIsland, newIsland) }
                    .onFailure { SkyLite.logger.error("island listener failed", it) }
            }
        }

        for (listener in asyncListeners) {
            SkyLite.scope.launch {
                runCatching { listener(oldIsland, newIsland) }
                    .onFailure { SkyLite.logger.error("async island listener failed", it) }
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
