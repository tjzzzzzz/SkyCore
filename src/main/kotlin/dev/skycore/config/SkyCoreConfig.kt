package dev.skycore.config

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler
import dev.isxander.yacl3.config.v2.api.SerialEntry
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder
import dev.skycore.SkyCore
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier

class SkyCoreConfig {

    @SerialEntry
    var enabled: Boolean = true

    @SerialEntry
    var hudScale: Float = 1.0f

    @SerialEntry
    var apiKey: String = ""

    @SerialEntry
    var keybinds: MutableMap<String, String> = mutableMapOf()

    @SerialEntry
    var hud: MutableMap<String, HudPlacement> = mutableMapOf()

    @SerialEntry
    var infoBar: InfoBarOptions = InfoBarOptions()

    @SerialEntry
    var toggleSprint: ToggleSprintOptions = ToggleSprintOptions()

    @SerialEntry
    var serverPack: ServerPackOptions = ServerPackOptions()

    @SerialEntry
    var itemScaleAnimation: ItemScaleAnimationOptions = ItemScaleAnimationOptions()

    @SerialEntry
    var abilityAlert: AbilityAlertOptions = AbilityAlertOptions()

    @SerialEntry
    var corpseHighlight: CorpseHighlightOptions = CorpseHighlightOptions()

    @SerialEntry
    var ghostVision: GhostVisionOptions = GhostVisionOptions()

    @SerialEntry
    var scathaMining: ScathaMiningOptions = ScathaMiningOptions()

    @SerialEntry
    var endNodeHighlight: EndNodeHighlightOptions = EndNodeHighlightOptions()

    @SerialEntry
    var gemstoneDesyncFix: GemstoneDesyncFixOptions = GemstoneDesyncFixOptions()

    @SerialEntry
    var breakResetFix: BreakResetFixOptions = BreakResetFixOptions()

    @SerialEntry
    var shaftAnnounce: ShaftAnnounceOptions = ShaftAnnounceOptions()

    @SerialEntry
    var commissionHighlight: CommissionHighlightOptions = CommissionHighlightOptions()

    @SerialEntry
    var commissionsDisplay: CommissionsDisplayOptions = CommissionsDisplayOptions()

    @SerialEntry
    var slotBinding: SlotBindingOptions = SlotBindingOptions()

    @SerialEntry
    var chatTweaks: ChatTweaksOptions = ChatTweaksOptions()

    @SerialEntry
    var commandKeybinds: CommandKeybindsOptions = CommandKeybindsOptions()

    @SerialEntry
    var chatRules: ChatRulesOptions = ChatRulesOptions()

    @SerialEntry
    var commandShortcuts: CommandShortcutsOptions = CommandShortcutsOptions()

    @SerialEntry
    var fullbright: FullbrightOptions = FullbrightOptions()

    @SerialEntry
    var itemProtection: ItemProtectionOptions = ItemProtectionOptions()

    @SerialEntry
    var infoTooltips: InfoTooltipsOptions = InfoTooltipsOptions()

    @SerialEntry
    var chatWaypoints: ChatWaypointsOptions = ChatWaypointsOptions()

    @SerialEntry
    var etherwarpOverlay: EtherwarpOverlayOptions = EtherwarpOverlayOptions()

    @SerialEntry
    var partyCommands: PartyCommandsOptions = PartyCommandsOptions()

    @SerialEntry
    var noRender: NoRenderOptions = NoRenderOptions()

    @SerialEntry
    var skillTracker: SkillTrackerOptions = SkillTrackerOptions()

    class HudPlacement {
        var x: Float = 0.01f
        var y: Float = 0.04f
        var scale: Float = 1.0f
    }

    class InfoBarOptions {
        var enabled: Boolean = false
        var fps: Boolean = true
        var ping: Boolean = true
        var tps: Boolean = true
        var day: Boolean = true
    }

    class ToggleSprintOptions {
        var enabled: Boolean = false
        var showHud: Boolean = true
    }

    class ServerPackOptions {
        var disable: Boolean = false
        var legacyPackInstalled: Boolean = false
        var autoEnableLegacy: Boolean = true
    }

    class ItemScaleAnimationOptions {
        var enabled: Boolean = false
        var size: Float = 1f
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = 0f
        var yaw: Float = 0f
        var pitch: Float = 0f
        var roll: Float = 0f
        var ignoreEffects: Boolean = false
        var speed: Float = 6f
        var noSwing: Boolean = false
        var noEquipReset: Boolean = false
        var disableReSwing: Boolean = true
    }

    class AbilityAlertOptions {
        var enabled: Boolean = false
        var overrideTicks: Int = 0
    }

    class CorpseHighlightOptions {
        var enabled: Boolean = false
        var hideOpened: Boolean = true
    }

    class GhostVisionOptions {
        var enabled: Boolean = false
    }

    class ScathaMiningOptions {
        var enabled: Boolean = false
        var alert: Boolean = true
        var cooldown: Boolean = true
    }

    class EndNodeHighlightOptions {
        var enabled: Boolean = false
    }

    class GemstoneDesyncFixOptions {
        var enabled: Boolean = false
    }

    class BreakResetFixOptions {
        var enabled: Boolean = false
    }

    class ShaftAnnounceOptions {
        var enabled: Boolean = false
    }

    class CommissionHighlightOptions {
        var enabled: Boolean = false
    }

    class CommissionsDisplayOptions {
        var enabled: Boolean = false
    }

    class SlotBindingOptions {
        var enabled: Boolean = false
        var keybindName: String = "key.keyboard.unknown"
        var binds: MutableMap<String, HotbarBind> = mutableMapOf()
    }

    class HotbarBind {
        var last: Int = 0
        var binds: MutableList<Int> = mutableListOf()
    }

    class ChatTweaksOptions {
        var enabled: Boolean = false
        var keepHistory: Boolean = false
        var extraLines: Boolean = false
        var lineLimit: Int = 1000
        var copyOnRightClick: Boolean = true
        var trimOnCopy: Boolean = false
        var msgOnCopy: Boolean = true
        var copyPreviewLength: Int = 50
    }

    class CommandKeybindsOptions {
        var enabled: Boolean = false
        var allowAllInGui: Boolean = false
        var binds: MutableList<CommandBind> = mutableListOf()
    }

    class CommandBind {
        var name: String = "New Keybind"
        var keyCode: Int = -1
        var command: String = ""
        var enabled: Boolean = true
        var allowInGui: Boolean = false
        var modifier: String = "Any"
        var islandFilter: String = ""
    }

    class ChatRulesOptions {
        var enabled: Boolean = false
        var rules: MutableList<ChatRuleEntry> = mutableListOf()
    }

    class ChatRuleEntry {
        var name: String = "New Rule"
        var match: String = ""
        var enabled: Boolean = false
        var caseSensitive: Boolean = false
        var matchType: String = "Equals"
        var cancel: Boolean = false
        var classFilter: String = ""
        var islandFilter: String = ""
        var title: String = ""
        var titleFadeIn: Int = 0
        var titleStay: Int = 30
        var titleFadeOut: Int = 10
        var customTitle: String = ""
        var customTitleStay: Int = 40
        var sound: String = ""
        var soundVolume: Float = 1f
        var soundPitch: Float = 1f
    }

    class CommandShortcutsOptions {
        var enabled: Boolean = false
        var shortcuts: MutableList<CommandShortcutEntry> = mutableListOf()
    }

    class CommandShortcutEntry {
        var shortcut: String = ""
        var message: String = ""
    }

    class FullbrightOptions {
        var enabled: Boolean = false
        var mode: String = "Ambient"
        var noEffect: Boolean = false
    }

    class ItemProtectionOptions {
        var enabled: Boolean = false
        var protectStarred: Boolean = false
        var protectRecomb: Boolean = false
        var protectMaxQuality: Boolean = false
        var protectValue: Boolean = false
        var protectValueMin: Double = 5_000_000.0
        var uuidList: MutableList<String> = mutableListOf()
        var idList: MutableList<String> = mutableListOf()
    }

    class InfoTooltipsOptions {
        var enabled: Boolean = false
        var dungeonQuality: Boolean = false
        var createdDate: Boolean = false
        var hexColor: Boolean = false
        var skyblockId: Boolean = false
    }

    class ChatWaypointsOptions {
        var enabled: Boolean = false
        var partyEnabled: Boolean = false
        var partyDuration: Int = 120
        var partyClearOnArrive: Boolean = false
        var allEnabled: Boolean = false
        var allDuration: Int = 60
        var allClearOnArrive: Boolean = false
    }

    class EtherwarpOverlayOptions {
        var enabled: Boolean = false
    }

    class PartyCommandsOptions {
        var enabled: Boolean = false
        var prefixes: String = "! ?"
        var self: Boolean = false
        var warp: Boolean = true
        var transfer: Boolean = true
        var allInvite: Boolean = true
        var coords: Boolean = true
        var kick: Boolean = true
        var queue: Boolean = true
        var downtime: Boolean = true
        var whitelist: MutableList<String> = mutableListOf()
        var blacklist: MutableList<String> = mutableListOf()
    }

    class NoRenderOptions {
        var enabled: Boolean = false
        var explosions: Boolean = false
        var emptyTooltips: Boolean = false
        var fireOverlay: Boolean = false
        var breakParticles: Boolean = false
        var bossBar: Boolean = false
        var armorBar: Boolean = false
        var foodBar: Boolean = false
        var fog: Boolean = false
        var effectDisplay: Boolean = false
        var recipeBook: Boolean = false
        var selectedItemName: Boolean = false
        var deadEntities: Boolean = false
        var deadPoof: Boolean = false
        var lightning: Boolean = false
        var fallingBlocks: Boolean = false
        var entityFire: Boolean = false
        var mageBeam: Boolean = false
        var iceSpray: Boolean = false
        var powderCoating: Boolean = false
        var soulweaverSkulls: Boolean = false
        var guidedSheep: Boolean = false
        var bonePlating: Boolean = false
        var healerFairy: Boolean = false
        var treeBits: Boolean = false
        var nausea: Boolean = false
        var expOrbs: Boolean = false
    }

    class SkillTrackerOptions {
        var enabled: Boolean = false
        var active: MutableMap<String, Boolean> = mutableMapOf()
    }

    companion object {

        private val handler: ConfigClassHandler<SkyCoreConfig> =
            ConfigClassHandler.createBuilder(SkyCoreConfig::class.java)
                .id(Identifier.fromNamespaceAndPath(SkyCore.MOD_ID, "config"))
                .serializer { definition ->
                    GsonConfigSerializerBuilder.create(definition)
                        .setPath(
                            FabricLoader.getInstance()
                                .configDir
                                .resolve("${SkyCore.MOD_ID}/config.json")
                        )
                        .setJson5(false)
                        .build()
                }
                .build()

        val instance: SkyCoreConfig get() = handler.instance()

        fun handler(): ConfigClassHandler<SkyCoreConfig> = handler

        fun load() {
            handler.load()
            SkyCore.logger.debug("config loaded, enabled={}", instance.enabled)
        }

        fun save() = handler.save()
    }
}
