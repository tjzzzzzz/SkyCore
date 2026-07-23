package dev.skycore.core.module

import dev.skycore.SkyCore
import dev.skycore.config.SkyCoreConfig
import dev.skycore.net.SkyCoreHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.skycore.ui.theme.Theme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.exists

object LegacyPackInstaller {

    private const val PROJECT_ID = "eiWiefXD"
    private const val VERSIONS_URL = "https://api.modrinth.com/v2/project/$PROJECT_ID/version"

    private const val LOCAL_FILE = "SkyBlock Legacy.zip"

    private val lock = Mutex()

    @Volatile
    var status: Status = Status.IDLE
        private set

    enum class Status { IDLE, DOWNLOADING, DONE, FAILED }

    fun ensureInstalled(autoEnable: Boolean) {
        val options = SkyCoreConfig.instance.serverPack
        val target = Minecraft.getInstance().resourcePackDirectory.resolve(LOCAL_FILE)

        if (options.legacyPackInstalled && target.exists()) {
            if (autoEnable) enablePack()
            return
        }
        if (status == Status.DOWNLOADING) return

        SkyCore.scope.launch {
            lock.withLock {
                if (status == Status.DOWNLOADING) return@withLock
                status = Status.DOWNLOADING
                toast("Downloading legacy textures…")
                runCatching { download(target) }
                    .onSuccess {
                        options.legacyPackInstalled = true
                        SkyCoreConfig.save()
                        status = Status.DONE
                        SkyCore.logger.info("legacy pack installed at {}", target)
                        toast("Legacy textures installed")
                        if (autoEnable) enablePack()
                    }
                    .onFailure {
                        status = Status.FAILED
                        SkyCore.logger.warn("legacy pack download failed", it)
                        toast("Legacy pack download failed")
                    }
            }
        }
    }

    private fun toast(message: String) {
        val client = Minecraft.getInstance()
        client.execute {
            val title = Component.literal("SkyCore")
                .setStyle(Style.EMPTY.withColor(Theme.ACCENT and 0xFFFFFF))
            SystemToast.addOrUpdate(
                client.gui.toastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                title,
                Component.literal(message)
            )
        }
    }

    private suspend fun download(target: java.nio.file.Path) {
        val client = SkyCoreHttp.instance
        val json = client.get(VERSIONS_URL).bodyAsText()
        val versions = SkyCoreHttp.json.decodeFromString<List<ModrinthVersion>>(json)

        val chosen = versions
            .sortedByDescending { it.datePublished }
            .let { sorted ->
                sorted.firstOrNull { v -> v.gameVersions.any { it.startsWith("26.") } } ?: sorted.firstOrNull()
            } ?: error("no versions returned by modrinth")

        val file = chosen.files.firstOrNull { it.primary } ?: chosen.files.firstOrNull()
        ?: error("version ${chosen.versionNumber} has no files")

        SkyCore.logger.info("downloading legacy pack {} from {}", chosen.versionNumber, file.url)

        val tmp = target.resolveSibling("$LOCAL_FILE.part")
        Files.newOutputStream(tmp).use { out ->
            client.get(file.url).bodyAsChannel().copyTo(out)
        }

        val sha1 = file.hashes?.sha1
        if (sha1 != null && !sha1.equals(sha1Of(tmp), ignoreCase = true)) {
            Files.deleteIfExists(tmp)
            error("sha1 mismatch, refusing to install a corrupt pack")
        }

        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun enablePack() {
        val client = Minecraft.getInstance()
        client.execute {

            client.reloadResourcePacks().thenRun {
                client.execute { selectLegacyPack(client) }
            }
        }
    }

    fun disablePack() {
        val client = Minecraft.getInstance()
        client.execute {
            val removed = client.options.resourcePacks.removeAll { it.contains(LOCAL_FILE) }
            if (!removed) return@execute
            client.resourcePackRepository.setSelected(client.options.resourcePacks)
            client.options.save()
            client.reloadResourcePacks()
            SkyCore.logger.info("disabled legacy pack")
        }
    }

    private fun selectLegacyPack(client: Minecraft) {
        val repo = client.resourcePackRepository
        val id = repo.availableIds.firstOrNull { it.contains(LOCAL_FILE) }

        if (id == null) {
            SkyCore.logger.warn(
                "legacy pack still not in repository, available ids: {}",
                repo.availableIds.joinToString()
            )
            return
        }

        val alreadyOn = client.options.resourcePacks.contains(id)
        if (!alreadyOn) client.options.resourcePacks.add(id)

        repo.setSelected(client.options.resourcePacks)
        client.options.save()

        client.reloadResourcePacks()
        SkyCore.logger.info("enabled legacy pack '{}' (was on: {})", id, alreadyOn)
    }

    private fun sha1Of(path: java.nio.file.Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class ModrinthVersion(
        @SerialName("version_number") val versionNumber: String = "",
        @SerialName("date_published") val datePublished: String = "",
        @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
        val files: List<ModrinthFile> = emptyList()
    )

    @Serializable
    private data class ModrinthFile(
        val url: String = "",
        val filename: String = "",
        val primary: Boolean = false,
        val hashes: Hashes? = null
    )

    @Serializable
    private data class Hashes(val sha1: String? = null)
}
