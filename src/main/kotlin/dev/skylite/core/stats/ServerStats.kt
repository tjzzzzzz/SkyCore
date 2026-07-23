package dev.skylite.core.stats

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket

/**
 * the numbers the info bar shows.
 *
 * tps is the interesting one. the client cannot ask the server how fast it is
 * ticking, so it has to be inferred. counting client ticks is useless because
 * the client ticks at its own 20/s regardless of the server, and so is watching
 * getGameTime() since the client advances that locally too.
 *
 * the one honest signal is the time sync packet: the server stamps it with its
 * own gameTime, so measuring how much server time passes per wall clock second
 * gives the real rate. [onTimeSync] is fed by the packet mixin.
 */
object ServerStats {

    /** samples kept for the rolling average, 5 sync packets is about 5 seconds */
    private const val WINDOW = 5

    private val samples = DoubleArray(WINDOW)
    private var sampleCount = 0
    private var sampleIndex = 0

    private var lastGameTime = 0L
    private var lastNanos = 0L

    /** cleared on disconnect so a stale reading never carries into a new server */
    @Volatile
    var tps: Double = 0.0
        private set

    val hasTps: Boolean get() = sampleCount > 0

    /**
     * called from the packet mixin on every time sync.
     *
     * intervals are measured in server ticks per real second rather than assumed
     * to be exactly 20 apart, which keeps it accurate even when the server
     * changes its sync cadence or skips one under load.
     */
    fun onTimeSync(gameTime: Long) {
        val now = System.nanoTime()

        if (lastNanos != 0L) {
            val elapsedSeconds = (now - lastNanos) / 1_000_000_000.0
            val ticks = gameTime - lastGameTime

            // ignore nonsense: clock rewinds, duplicate packets, long freezes
            if (elapsedSeconds > 0.05 && ticks in 1..400) {
                val rate = (ticks / elapsedSeconds).coerceIn(0.0, 20.0)
                samples[sampleIndex] = rate
                sampleIndex = (sampleIndex + 1) % WINDOW
                if (sampleCount < WINDOW) sampleCount++

                var sum = 0.0
                for (i in 0 until sampleCount) sum += samples[i]
                tps = sum / sampleCount
            }
        }

        lastGameTime = gameTime
        lastNanos = now
    }

    // ---- ping ----------------------------------------------------------

    private const val PING_WINDOW = 3
    private const val PING_INTERVAL_TICKS = 40

    private val pingSamples = IntArray(PING_WINDOW)
    private var pingCount = 0
    private var pingIndex = 0
    private var pingCooldown = 0

    /** our own measurement, 0 until the first pong lands */
    @Volatile
    var measuredPing: Int = 0
        private set

    val hasMeasuredPing: Boolean get() = pingCount > 0

    /**
     * hypixel drives the tab list itself and reports everyone at 1ms, so the
     * player list latency is decoration rather than data. sending our own ping
     * request and timing the pong is the only way to get a real number.
     */
    fun tickPing() {
        if (--pingCooldown > 0) return
        pingCooldown = PING_INTERVAL_TICKS

        val connection = Minecraft.getInstance().player?.connection ?: return
        connection.send(ServerboundPingRequestPacket(System.nanoTime()))
    }

    /** called from the pong mixin with the timestamp we stamped on the way out */
    fun onPong(sentNanos: Long) {
        val rtt = ((System.nanoTime() - sentNanos) / 1_000_000L).toInt()
        // a pong we did not send, or a clock oddity
        if (rtt < 0 || rtt > 10_000) return

        pingSamples[pingIndex] = rtt
        pingIndex = (pingIndex + 1) % PING_WINDOW
        if (pingCount < PING_WINDOW) pingCount++

        var sum = 0
        for (i in 0 until pingCount) sum += pingSamples[i]
        measuredPing = sum / pingCount
    }

    fun reset() {
        sampleCount = 0
        sampleIndex = 0
        lastGameTime = 0L
        lastNanos = 0L
        tps = 0.0

        pingCount = 0
        pingIndex = 0
        pingCooldown = 0
        measuredPing = 0
    }

    fun fps(): Int = Minecraft.getInstance().fps

    /**
     * our measured round trip, falling back to the player list only if the
     * server never answers a ping request.
     */
    fun ping(): Int {
        if (hasMeasuredPing) return measuredPing

        val client = Minecraft.getInstance()
        val player = client.player ?: return 0
        return client.player?.connection?.getPlayerInfo(player.uuid)?.latency ?: 0
    }

    /** in game day, the overworld clock replaced the old dayTime field in 26.2 */
    fun day(): Long {
        val level = Minecraft.getInstance().level ?: return 0
        return level.overworldClockTime / 24000L
    }
}
