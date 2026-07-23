package dev.skycore.core.stats

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket

object ServerStats {

    private const val WINDOW = 5

    private val samples = DoubleArray(WINDOW)
    private var sampleCount = 0
    private var sampleIndex = 0

    private var lastGameTime = 0L
    private var lastNanos = 0L

    @Volatile
    var tps: Double = 0.0
        private set

    val hasTps: Boolean get() = sampleCount > 0

    fun onTimeSync(gameTime: Long) {
        val now = System.nanoTime()

        if (lastNanos != 0L) {
            val elapsedSeconds = (now - lastNanos) / 1_000_000_000.0
            val ticks = gameTime - lastGameTime

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

    private const val PING_WINDOW = 3
    private const val PING_INTERVAL_TICKS = 40

    private val pingSamples = IntArray(PING_WINDOW)
    private var pingCount = 0
    private var pingIndex = 0
    private var pingCooldown = 0

    @Volatile
    var measuredPing: Int = 0
        private set

    val hasMeasuredPing: Boolean get() = pingCount > 0

    fun tickPing() {
        if (--pingCooldown > 0) return
        pingCooldown = PING_INTERVAL_TICKS

        val connection = Minecraft.getInstance().player?.connection ?: return
        connection.send(ServerboundPingRequestPacket(System.nanoTime()))
    }

    fun onPong(sentNanos: Long) {
        val rtt = ((System.nanoTime() - sentNanos) / 1_000_000L).toInt()

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

    fun ping(): Int {
        if (hasMeasuredPing) return measuredPing

        val client = Minecraft.getInstance()
        val player = client.player ?: return 0
        return client.player?.connection?.getPlayerInfo(player.uuid)?.latency ?: 0
    }

    fun day(): Long {
        val level = Minecraft.getInstance().level ?: return 0
        return level.overworldClockTime / 24000L
    }
}
