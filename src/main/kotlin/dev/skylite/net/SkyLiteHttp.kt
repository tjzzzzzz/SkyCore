package dev.skylite.net

import dev.skylite.SkyLite
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * one shared ktor client for the whole mod. spinning up a client per feature
 * means a thread pool per feature, which is exactly the kind of thing the
 * 1.8.9 clients did wrong.
 */
object SkyLiteHttp {

    private var client: HttpClient? = null

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    val instance: HttpClient
        get() = client ?: error("SkyLiteHttp used before init()")

    fun init() {
        if (client != null) return

        client = HttpClient(CIO) {
            expectSuccess = false

            engine {
                maxConnectionsCount = 32
            }

            install(ContentNegotiation) { json(json) }

            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 15_000
            }

            install(UserAgent) {
                agent = "SkyLite/${SkyLite.version}"
            }
        }

        SkyLite.logger.debug("http client up")
    }

    /** called on game shutdown so cio does not keep the jvm alive */
    fun shutdown() {
        client?.close()
        client = null
    }
}
