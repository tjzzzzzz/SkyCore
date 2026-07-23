package dev.skylite

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.command.SkyLiteCommands
import dev.skylite.core.keybind.SkyLiteKeys
import dev.skylite.core.location.LocationManager
import dev.skylite.core.module.ServerPackControl
import dev.skylite.core.module.ToggleSprint
import dev.skylite.core.module.general.ChatRules
import dev.skylite.core.module.general.ChatTweaks
import dev.skylite.core.module.general.ChatWaypoints
import dev.skylite.core.module.general.CommandKeybinds
import dev.skylite.core.module.general.CommandShortcuts
import dev.skylite.core.module.general.EtherwarpOverlay
import dev.skylite.core.module.general.Fullbright
import dev.skylite.core.module.general.InfoTooltips
import dev.skylite.core.module.general.ItemProtection
import dev.skylite.core.module.general.NoRender
import dev.skylite.core.module.general.PartyCommands
import dev.skylite.core.module.general.SkillTracker
import dev.skylite.core.module.general.SlotBinding
import dev.skylite.core.module.mining.AbilityAlert
import dev.skylite.core.module.mining.BreakResetFix
import dev.skylite.core.module.mining.CommissionHighlight
import dev.skylite.core.module.mining.CommissionsDisplay
import dev.skylite.core.module.mining.CorpseHighlight
import dev.skylite.core.module.mining.EndNodeHighlight
import dev.skylite.core.module.mining.GemstoneDesyncFix
import dev.skylite.core.module.mining.GhostVision
import dev.skylite.core.module.mining.ScathaMining
import dev.skylite.core.module.mining.ShaftAnnounce
import dev.skylite.core.render.WorldBoxes
import dev.skylite.core.skyblock.PartyChat
import dev.skylite.core.skyblock.TabListCache
import dev.skylite.net.SkyLiteHttp
import dev.skylite.ui.hud.HudManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

object SkyLite : ClientModInitializer {

    const val MOD_ID = "skylite"
    const val MOD_NAME = "SkyLite"

    val logger: Logger = LoggerFactory.getLogger(MOD_NAME)

    val isDev: Boolean by lazy {
        FabricLoader.getInstance().isDevelopmentEnvironment ||
            System.getProperty("skylite.dev").toBoolean()
    }

    val version: String by lazy {
        FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName(MOD_NAME))

    override fun onInitializeClient() {
        val started = TimeSource.Monotonic.markNow()

        logger.info("booting $MOD_NAME $version (dev=$isDev)")

        SkyLiteConfig.load()
        SkyLiteHttp.init()
        LocationManager.init()
        TabListCache.init()
        WorldBoxes.init()
        SkyLiteKeys.init()
        SkyLiteCommands.init()
        HudManager.init()
        ToggleSprint.init()
        ServerPackControl.init()

        PartyChat.init()
        Fullbright.init()
        SlotBinding.init()
        ChatTweaks.init()
        CommandKeybinds.init()
        ChatRules.init()
        CommandShortcuts.init()
        ChatWaypoints.init()
        EtherwarpOverlay.init()
        PartyCommands.init()
        NoRender.init()
        ItemProtection.init()
        InfoTooltips.init()
        SkillTracker.init()

        AbilityAlert.init()
        CorpseHighlight.init()
        GhostVision.init()
        ScathaMining.init()
        EndNodeHighlight.init()
        GemstoneDesyncFix.init()
        BreakResetFix.init()
        ShaftAnnounce.init()
        CommissionHighlight.init()
        CommissionsDisplay.init()

        logger.info("ready in ${started.elapsedNow().inWholeMilliseconds}ms")
    }
}
