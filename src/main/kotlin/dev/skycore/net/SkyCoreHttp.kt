package dev.skycore.net

import dev.skycore.SkyCore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object SkyCoreHttp {

    private var client: HttpClient? = null

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    val instance: HttpClient
        get() = client ?: error("SkyCoreHttp used before init()")

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
                agent = "SkyCore/${SkyCore.version}"
            }
        }

        SkyCore.logger.debug("http client up")
    }

    fun shutdown() {
        client?.close()
        client = null
    }
}
