package dev.skycore

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.command.SkyCoreCommands
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.keybind.SkyCoreKeys
import dev.skycore.core.location.LocationManager
import dev.skycore.core.module.ServerPackControl
import dev.skycore.core.module.ToggleSprint
import dev.skycore.core.module.dungeon.ClassNametags
import dev.skycore.core.module.dungeon.CroesusSolver
import dev.skycore.core.module.dungeon.DeviceSolvers
import dev.skycore.core.module.dungeon.DungeonChestValue
import dev.skycore.core.module.dungeon.KeyHighlight
import dev.skycore.core.module.dungeon.LeapOverlay
import dev.skycore.core.module.dungeon.LividSolver
import dev.skycore.core.module.dungeon.MelodyMessage
import dev.skycore.core.module.dungeon.MimicMessage
import dev.skycore.core.module.dungeon.MinibossHighlight
import dev.skycore.core.module.dungeon.PlatformHighlight
import dev.skycore.core.module.dungeon.PrinceMessage
import dev.skycore.core.module.dungeon.QuickClose
import dev.skycore.core.module.dungeon.RelicHighlight
import dev.skycore.core.module.dungeon.ScoreCalculator
import dev.skycore.core.module.dungeon.SecretBatHighlight
import dev.skycore.core.module.dungeon.SecretChime
import dev.skycore.core.module.dungeon.SpiritBowHighlight
import dev.skycore.core.module.dungeon.StarredMobHighlight
import dev.skycore.core.module.dungeon.TerminalSolvers
import dev.skycore.core.module.dungeon.TerracottaTimer
import dev.skycore.core.module.dungeon.WitherDragons
import dev.skycore.core.module.general.ChatRules
import dev.skycore.core.module.general.ChatTweaks
import dev.skycore.core.module.general.ChatWaypoints
import dev.skycore.core.module.general.CommandKeybinds
import dev.skycore.core.module.general.CommandShortcuts
import dev.skycore.core.module.general.EtherwarpOverlay
import dev.skycore.core.module.general.Fullbright
import dev.skycore.core.module.general.InfoTooltips
import dev.skycore.core.module.general.ItemProtection
import dev.skycore.core.module.general.NoRender
import dev.skycore.core.module.general.PartyCommands
import dev.skycore.core.module.general.SkillTracker
import dev.skycore.core.module.general.SlotBinding
import dev.skycore.core.module.mining.AbilityAlert
import dev.skycore.core.module.mining.BreakResetFix
import dev.skycore.core.module.mining.CommissionHighlight
import dev.skycore.core.module.mining.CommissionsDisplay
import dev.skycore.core.module.mining.CorpseHighlight
import dev.skycore.core.module.mining.EndNodeHighlight
import dev.skycore.core.module.mining.GemstoneDesyncFix
import dev.skycore.core.module.mining.GhostVision
import dev.skycore.core.module.mining.ScathaMining
import dev.skycore.core.module.mining.ShaftAnnounce
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.render.WorldLabels
import dev.skycore.core.skyblock.PartyChat
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.net.SkyCoreHttp
import dev.skycore.ui.hud.HudManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

object SkyCore : ClientModInitializer {

    const val MOD_ID = "skycore"
    const val MOD_NAME = "SkyCore"

    val logger: Logger = LoggerFactory.getLogger(MOD_NAME)

    val isDev: Boolean by lazy {
        FabricLoader.getInstance().isDevelopmentEnvironment ||
            System.getProperty("skycore.dev").toBoolean()
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

        SkyCoreConfig.load()
        SkyCoreHttp.init()
        LocationManager.init()
        TabListCache.init()
        WorldBoxes.init()
        WorldLabels.init()
        DungeonUtil.init()
        SkyCoreKeys.init()
        SkyCoreCommands.init()
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

        TerminalSolvers.init()
        DeviceSolvers.init()
        LeapOverlay.init()
        CroesusSolver.init()
        DungeonChestValue.init()
        QuickClose.init()
        MelodyMessage.init()
        SecretChime.init()
        WitherDragons.init()
        ScoreCalculator.init()
        MimicMessage.init()
        PrinceMessage.init()
        TerracottaTimer.init()
        KeyHighlight.init()
        LividSolver.init()
        SecretBatHighlight.init()
        SpiritBowHighlight.init()
        RelicHighlight.init()
        ClassNametags.init()
        PlatformHighlight.init()
        MinibossHighlight.init()
        StarredMobHighlight.init()

        logger.info("ready in ${started.elapsedNow().inWholeMilliseconds}ms")
    }
}
