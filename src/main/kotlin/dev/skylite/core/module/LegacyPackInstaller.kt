package dev.skylite.core.module

import dev.skylite.SkyLite
import dev.skylite.config.SkyLiteConfig
import dev.skylite.net.SkyLiteHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.skylite.ui.theme.Theme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.exists

/**
 * fetches the "Hypixel SkyBlock Legacy" resource pack from its official
 * modrinth page and drops it into the player's resourcepacks folder.
 *
 * the pack is All Rights Reserved, so it is deliberately NOT bundled in our jar.
 * instead each user downloads it from the author's own distribution channel, the
 * same file they would get by clicking download on modrinth, so nothing
 * copyrighted is ever redistributed by us. this is the launcher approach: fetch
 * from source, never rehost.
 */
object LegacyPackInstaller {

    /** modrinth project id for hypixel-skyblock-legacy */
    private const val PROJECT_ID = "eiWiefXD"
    private const val VERSIONS_URL = "https://api.modrinth.com/v2/project/$PROJECT_ID/version"

    /** stable local name so we can find it again regardless of pack version */
    private const val LOCAL_FILE = "SkyBlock Legacy.zip"

    /** guards against two toggles kicking off overlapping downloads */
    private val lock = Mutex()

    @Volatile
    var status: Status = Status.IDLE
        private set

    enum class Status { IDLE, DOWNLOADING, DONE, FAILED }

    /**
     * downloads the pack unless it is already present or already installed once.
     * safe to call from the render thread, the work hops onto our io scope.
     */
    fun ensureInstalled(autoEnable: Boolean) {
        val options = SkyLiteConfig.instance.serverPack
        val target = Minecraft.getInstance().resourcePackDirectory.resolve(LOCAL_FILE)

        if (options.legacyPackInstalled && target.exists()) {
            if (autoEnable) enablePack()
            return
        }
        if (status == Status.DOWNLOADING) return

        SkyLite.scope.launch {
            lock.withLock {
                if (status == Status.DOWNLOADING) return@withLock
                status = Status.DOWNLOADING
                toast("Downloading legacy textures…")
                runCatching { download(target) }
                    .onSuccess {
                        options.legacyPackInstalled = true
                        SkyLiteConfig.save()
                        status = Status.DONE
                        SkyLite.logger.info("legacy pack installed at {}", target)
                        toast("Legacy textures installed")
                        if (autoEnable) enablePack()
                    }
                    .onFailure {
                        status = Status.FAILED
                        SkyLite.logger.warn("legacy pack download failed", it)
                        toast("Legacy pack download failed")
                    }
            }
        }
    }

    /**
     * shows a toast so the download is not a silent pause. addOrUpdate reuses one
     * toast id, so "downloading" is replaced in place by "installed" rather than
     * stacking a second notification.
     */
    private fun toast(message: String) {
        val client = Minecraft.getInstance()
        client.execute {
            val title = Component.literal("SkyLite")
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
        val client = SkyLiteHttp.instance
        val json = client.get(VERSIONS_URL).bodyAsText()
        val versions = SkyLiteHttp.json.decodeFromString<List<ModrinthVersion>>(json)

        // prefer a build that lists a 26.x game version, newest first, else just
        // the newest published overall
        val chosen = versions
            .sortedByDescending { it.datePublished }
            .let { sorted ->
                sorted.firstOrNull { v -> v.gameVersions.any { it.startsWith("26.") } } ?: sorted.firstOrNull()
            } ?: error("no versions returned by modrinth")

        val file = chosen.files.firstOrNull { it.primary } ?: chosen.files.firstOrNull()
        ?: error("version ${chosen.versionNumber} has no files")

        SkyLite.logger.info("downloading legacy pack {} from {}", chosen.versionNumber, file.url)

        // stream to a temp file first so a half download never looks installed
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

    /**
     * turns the pack on in the pack list, on the render thread.
     *
     * the repository only rescans the folder from a full client reload, calling
     * repo.reload() in isolation leaves availableIds stale, so we reload the
     * client first, then look our pack up in the refreshed list.
     */
    private fun enablePack() {
        val client = Minecraft.getInstance()
        client.execute {
            // a full reload rebuilds the repository from disk, which is what makes
            // the just-written file discoverable
            client.reloadResourcePacks().thenRun {
                client.execute { selectLegacyPack(client) }
            }
        }
    }

    /**
     * removes the legacy pack from the active selection and reloads, so the
     * player's own packs (or vanilla) show again. matches by filename rather
     * than a repo lookup so it works even if the repository view is stale.
     */
    fun disablePack() {
        val client = Minecraft.getInstance()
        client.execute {
            val removed = client.options.resourcePacks.removeAll { it.contains(LOCAL_FILE) }
            if (!removed) return@execute
            client.resourcePackRepository.setSelected(client.options.resourcePacks)
            client.options.save()
            client.reloadResourcePacks()
            SkyLite.logger.info("disabled legacy pack")
        }
    }

    private fun selectLegacyPack(client: Minecraft) {
        val repo = client.resourcePackRepository
        val id = repo.availableIds.firstOrNull { it.contains(LOCAL_FILE) }

        if (id == null) {
            SkyLite.logger.warn(
                "legacy pack still not in repository, available ids: {}",
                repo.availableIds.joinToString()
            )
            return
        }

        // sits above the server pack (which the ordering mixins have unpinned),
        // so legacy textures win and hypixel's pack fills whatever they miss
        val alreadyOn = client.options.resourcePacks.contains(id)
        if (!alreadyOn) client.options.resourcePacks.add(id)

        repo.setSelected(client.options.resourcePacks)
        client.options.save()
        // reload even when already selected, so a re-toggle re-applies the
        // layering against the current server pack
        client.reloadResourcePacks()
        SkyLite.logger.info("enabled legacy pack '{}' (was on: {})", id, alreadyOn)
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
