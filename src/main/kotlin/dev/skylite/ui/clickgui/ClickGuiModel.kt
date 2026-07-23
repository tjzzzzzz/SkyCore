package dev.skylite.ui.clickgui

import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.keybind.SkyLiteKeys
import dev.skylite.core.module.LegacyPackInstaller
import dev.skylite.core.module.ServerPackControl
import dev.skylite.ui.general.ChatRulesScreen
import dev.skylite.ui.general.CommandKeybindsScreen
import dev.skylite.ui.general.CommandShortcutsScreen
import dev.skylite.ui.general.SlotBindingScreen
import dev.skylite.ui.render.Fonts
import dev.skylite.ui.visuals.ItemScaleEditorScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * what the sidebar shows, in display order. the label is built once, components
 * are immutable so there is no reason to rebuild one every frame.
 */
enum class GuiCategory(val displayName: String) {
    GENERAL("General"),
    VISUALS("Visuals"),
    MINING("Mining"),
    DUNGEONS("Dungeons"),
    ECONOMY("Economy"),
    WAYPOINTS("Waypoints");

    val label: Component = Fonts.label(displayName, Fonts.MEDIUM)
}

/**
 * one row in the right pane. [hover] is animation scratch space owned by the
 * renderer, everything else is read straight from whatever backs the option so
 * the gui never holds a second copy of the state.
 */
sealed class GuiOption(val title: String, val description: String) {
    var hover: Float = 0f

    val titleLabel: Component = Fonts.label(title, Fonts.MEDIUM)
    val descLabel: Component = Fonts.label(description, Fonts.SMALL)

    /** sub options, revealed when the parent row is expanded */
    var children: List<GuiOption> = emptyList()
    var expanded: Boolean = false

    val hasChildren: Boolean get() = children.isNotEmpty()

    /**
     * search synonyms. these are the words someone actually types when they are
     * looking for a feature but do not remember what we called it.
     */
    var keywords: List<String> = emptyList()
}

/** keeps the concrete type so it chains without a cast at every call site */
fun <T : GuiOption> T.withKeywords(vararg words: String): T {
    keywords = words.toList()
    return this
}

class ToggleOption(
    title: String,
    description: String,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit
) : GuiOption(title, description) {

    var enabled: Boolean
        get() = getter()
        set(value) = setter(value)

    /** knob travel, follows [enabled] */
    var knob: Float = if (getter()) 1f else 0f

    /**
     * optional live editor, drawn as an Edit badge next to the toggle. used by
     * features like viewmodel where tweaking only makes sense in first person.
     */
    var editAction: ((Screen) -> Unit)? = null

    fun toggle() {
        enabled = !enabled
    }
}

fun ToggleOption.withEditor(action: (Screen) -> Unit): ToggleOption {
    editAction = action
    return this
}

/**
 * a rebindable key. the mapping is the vanilla one, so a change here shows up in
 * the controls screen too and gets persisted by [SkyLiteKeys].
 */
class KeybindOption(
    title: String,
    description: String,
    val mapping: KeyMapping
) : GuiOption(title, description) {

    val boundKeyLabel: String get() = SkyLiteKeys.labelOf(mapping)
}

/**
 * a draggable numeric row. values are clamped to [min]..[max] and snapped to
 * [step] so the config never drifts into ugly floats from mouse jitter.
 */
class SliderOption(
    title: String,
    description: String,
    val min: Float,
    val max: Float,
    val step: Float,
    private val getter: () -> Float,
    private val setter: (Float) -> Unit,
    private val format: (Float) -> String = { v ->
        if (step >= 1f) v.toInt().toString() else "%.2f".format(v)
    }
) : GuiOption(title, description) {

    private var cachedText: String = ""
    private var cachedLabel: Component = Fonts.label("", Fonts.SMALL)

    var value: Float
        get() = getter()
        set(v) = setter(snap(v))

    val valueLabel: Component
        get() {
            val text = format(value)
            if (text != cachedText) {
                cachedText = text
                cachedLabel = Fonts.label(text, Fonts.SMALL)
            }
            return cachedLabel
        }

    fun setFromProgress(progress: Float) {
        value = min + (max - min) * progress.coerceIn(0f, 1f)
    }

    fun progress(): Float {
        if (max <= min) return 0f
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    private fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(min, max)
        if (step <= 0f) return clamped
        val steps = kotlin.math.round((clamped - min) / step)
        return (min + steps * step).coerceIn(min, max)
    }
}

/**
 * the option tree. features register here as they get built, which keeps the
 * screen itself free of any knowledge about individual modules.
 */
object ClickGuiRegistry {

    private val options = LinkedHashMap<GuiCategory, MutableList<GuiOption>>()

    /**
     * scratch storage for options that do not have a config field yet, so the
     * gui is usable before every feature exists. anything that should survive a
     * restart needs a real @SerialEntry on SkyLiteConfig instead.
     */
    private val transient = HashMap<String, Boolean>()

    /** built lazily so the registry does not touch config during class init */
    private var built = false

    private fun build() {
        if (built) return
        built = true

        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Enable SkyLite",
                "Master switch for every module.",
                { SkyLiteConfig.instance.enabled },
                { SkyLiteConfig.instance.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("master", "toggle", "on", "off", "disable", "enable")
        )
        register(
            GuiCategory.GENERAL,
            KeybindOption(
                "Open Menu",
                "Optional hotkey. /skylite always works. Delete unbinds.",
                SkyLiteKeys.openClickGui
            ).withKeywords("keybind", "key", "bind", "hotkey", "shortcut", "gui", "menu", "command")
        )
        val infoBar = SkyLiteConfig.instance.infoBar
        val infoBarOption = ToggleOption(
            "Info Bar",
            "FPS, ping, server TPS and lobby day.",
            { infoBar.enabled },
            { infoBar.enabled = it; SkyLiteConfig.save() }
        )
        infoBarOption.withKeywords("hud", "overlay", "stats", "counter", "fps", "ping", "tps", "day", "performance")
        infoBarOption.children = listOf(
            ToggleOption("FPS", "Client frame rate.", { infoBar.fps }, { infoBar.fps = it; SkyLiteConfig.save() })
                .withKeywords("framerate", "frames", "performance", "lag", "smooth"),
            ToggleOption("Ping", "Round trip to the server.", { infoBar.ping }, { infoBar.ping = it; SkyLiteConfig.save() })
                .withKeywords("latency", "ms", "lag", "connection", "network"),
            ToggleOption("Server TPS", "Measured from server time sync.", { infoBar.tps }, { infoBar.tps = it; SkyLiteConfig.save() })
                .withKeywords("ticks", "tickrate", "lag", "server", "performance"),
            ToggleOption("Day", "In game day of the lobby.", { infoBar.day }, { infoBar.day = it; SkyLiteConfig.save() })
                .withKeywords("lobby", "age", "time", "server")
        )
        register(GuiCategory.GENERAL, infoBarOption)

        val sprint = SkyLiteConfig.instance.toggleSprint
        val sprintOption = ToggleOption(
            "Toggle Sprint",
            "Keeps you sprinting without holding the key.",
            { sprint.enabled },
            { sprint.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("sprint", "run", "auto", "toggle", "speed", "movement", "autosprint")
        sprintOption.children = listOf(
            ToggleOption(
                "Show HUD", "Displays a SPRINT status pill.",
                { sprint.showHud }, { sprint.showHud = it; SkyLiteConfig.save() }
            ).withKeywords("hud", "overlay", "indicator", "pill", "status")
        )
        register(GuiCategory.GENERAL, sprintOption)

        val serverPack = SkyLiteConfig.instance.serverPack
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Legacy Textures",
                "Layers the legacy pack over Hypixel's, so old textures win.",
                { serverPack.disable },
                {
                    serverPack.disable = it
                    SkyLiteConfig.save()
                    if (it) {
                        // on: fetch+enable the legacy pack layered above hypixel's
                        // pack. the ordering mixins unpin the server pack so the
                        // legacy textures win while hypixel's fill the gaps
                        LegacyPackInstaller.ensureInstalled(serverPack.autoEnableLegacy)
                    } else {
                        // off: turn the legacy pack back off; the server pack
                        // re-pins to the top and fully takes over again
                        LegacyPackInstaller.disablePack()
                        ServerPackControl.refresh()
                    }
                }
            ).withKeywords(
                "texture", "resource", "pack", "resourcepack", "legacy", "vanilla",
                "server", "hypixel", "skyblock", "textures", "disable"
            )
        )

        register(
            GuiCategory.GENERAL,
            transientOption("Hide Ads", "Filters lobby advertisement spam from chat.")
                .withKeywords("chat", "spam", "filter", "advertisement", "clean")
        )

        val fullbright = SkyLiteConfig.instance.fullbright
        val fullbrightOption = ToggleOption(
            "Fullbright",
            "Brightens the world so caves stay readable.",
            { fullbright.enabled },
            { fullbright.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("fullbright", "gamma", "night", "vision", "brightness", "light")
        fullbrightOption.children = listOf(
            ToggleOption(
                "Ambient Mode",
                "Force full ambient lighting.",
                { fullbright.mode.equals("Ambient", true) },
                { if (it) { fullbright.mode = "Ambient"; SkyLiteConfig.save() } }
            ),
            ToggleOption(
                "Gamma Mode",
                "Push lightmap gamma extremely high.",
                { fullbright.mode.equals("Gamma", true) },
                { if (it) { fullbright.mode = "Gamma"; SkyLiteConfig.save() } }
            ),
            ToggleOption(
                "Potion Mode",
                "Apply night vision continuously.",
                { fullbright.mode.equals("Potion", true) },
                { if (it) { fullbright.mode = "Potion"; SkyLiteConfig.save() } }
            ),
            ToggleOption(
                "Clear Night Vision",
                "Remove night vision when not in Potion mode.",
                { fullbright.noEffect },
                { fullbright.noEffect = it; SkyLiteConfig.save() }
            )
        )
        register(GuiCategory.GENERAL, fullbrightOption)

        val chatWaypoints = SkyLiteConfig.instance.chatWaypoints
        val chatWaypointsOption = ToggleOption(
            "Chat Waypoints",
            "Creates waypoints from coordinates in chat.",
            { chatWaypoints.enabled },
            { chatWaypoints.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("waypoint", "coords", "coordinates", "party", "chat", "beam")
        chatWaypointsOption.children = listOf(
            ToggleOption("Party Chat", "Parse coordinates from party chat.", { chatWaypoints.partyEnabled }, { chatWaypoints.partyEnabled = it; SkyLiteConfig.save() }),
            SliderOption("Party Duration", "Seconds party waypoints last.", 10f, 600f, 10f, { chatWaypoints.partyDuration.toFloat() }, { chatWaypoints.partyDuration = it.toInt(); SkyLiteConfig.save() }),
            ToggleOption("Party Clear on Arrive", "Remove party waypoints when you get close.", { chatWaypoints.partyClearOnArrive }, { chatWaypoints.partyClearOnArrive = it; SkyLiteConfig.save() }),
            ToggleOption("All Chat", "Parse coordinates from public chat.", { chatWaypoints.allEnabled }, { chatWaypoints.allEnabled = it; SkyLiteConfig.save() }),
            SliderOption("All Duration", "Seconds public waypoints last.", 10f, 600f, 10f, { chatWaypoints.allDuration.toFloat() }, { chatWaypoints.allDuration = it.toInt(); SkyLiteConfig.save() }),
            ToggleOption("All Clear on Arrive", "Remove public waypoints when you get close.", { chatWaypoints.allClearOnArrive }, { chatWaypoints.allClearOnArrive = it; SkyLiteConfig.save() })
        )
        register(GuiCategory.GENERAL, chatWaypointsOption)

        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Etherwarp Overlay",
                "Highlights the block targeted by Ether Transmission.",
                { SkyLiteConfig.instance.etherwarpOverlay.enabled },
                { SkyLiteConfig.instance.etherwarpOverlay.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("etherwarp", "ether", "transmission", "aotv", "aote", "overlay")
        )

        val noRender = SkyLiteConfig.instance.noRender
        val noRenderOption = ToggleOption(
            "No Render",
            "Hides selected particles, entities and HUD clutter.",
            { noRender.enabled },
            { noRender.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("norender", "hide", "particles", "fog", "bossbar", "clean")
        noRenderOption.children = listOf(
            ToggleOption("Explosions", "Hide explosion particles.", { noRender.explosions }, { noRender.explosions = it; SkyLiteConfig.save() }),
            ToggleOption("Fire Overlay", "Hide first person fire.", { noRender.fireOverlay }, { noRender.fireOverlay = it; SkyLiteConfig.save() }),
            ToggleOption("Break Particles", "Hide block break particles.", { noRender.breakParticles }, { noRender.breakParticles = it; SkyLiteConfig.save() }),
            ToggleOption("Boss Bar", "Hide the boss health bar.", { noRender.bossBar }, { noRender.bossBar = it; SkyLiteConfig.save() }),
            ToggleOption("Armor Bar", "Hide armor HUD.", { noRender.armorBar }, { noRender.armorBar = it; SkyLiteConfig.save() }),
            ToggleOption("Food Bar", "Hide hunger HUD.", { noRender.foodBar }, { noRender.foodBar = it; SkyLiteConfig.save() }),
            ToggleOption("Fog", "Remove distance fog.", { noRender.fog }, { noRender.fog = it; SkyLiteConfig.save() }),
            ToggleOption("Effect Display", "Hide potion icons.", { noRender.effectDisplay }, { noRender.effectDisplay = it; SkyLiteConfig.save() }),
            ToggleOption("Recipe Book", "Hide inventory recipe book.", { noRender.recipeBook }, { noRender.recipeBook = it; SkyLiteConfig.save() }),
            ToggleOption("Selected Item Name", "Hide hotbar item name.", { noRender.selectedItemName }, { noRender.selectedItemName = it; SkyLiteConfig.save() }),
            ToggleOption("Empty Tooltips", "Disable blank GUI tooltips.", { noRender.emptyTooltips }, { noRender.emptyTooltips = it; SkyLiteConfig.save() }),
            ToggleOption("Dead Entities", "Hide dead mobs.", { noRender.deadEntities }, { noRender.deadEntities = it; SkyLiteConfig.save() }),
            ToggleOption("Dead Poof", "Hide death poof particles.", { noRender.deadPoof }, { noRender.deadPoof = it; SkyLiteConfig.save() }),
            ToggleOption("Lightning", "Hide lightning bolts.", { noRender.lightning }, { noRender.lightning = it; SkyLiteConfig.save() }),
            ToggleOption("Falling Blocks", "Hide falling block entities.", { noRender.fallingBlocks }, { noRender.fallingBlocks = it; SkyLiteConfig.save() }),
            ToggleOption("Entity Fire", "Hide fire on entities.", { noRender.entityFire }, { noRender.entityFire = it; SkyLiteConfig.save() }),
            ToggleOption("Mage Beam", "Hide mage beam fireworks in dungeons.", { noRender.mageBeam }, { noRender.mageBeam = it; SkyLiteConfig.save() }),
            ToggleOption("Ice Spray", "Hide ice spray particles.", { noRender.iceSpray }, { noRender.iceSpray = it; SkyLiteConfig.save() }),
            ToggleOption("Powder Coating", "Hide powder coating dust.", { noRender.powderCoating }, { noRender.powderCoating = it; SkyLiteConfig.save() }),
            ToggleOption("Soulweaver Skulls", "Hide soulweaver skulls in dungeons.", { noRender.soulweaverSkulls }, { noRender.soulweaverSkulls = it; SkyLiteConfig.save() }),
            ToggleOption("Guided Sheep", "Hide guided sheep in dungeons.", { noRender.guidedSheep }, { noRender.guidedSheep = it; SkyLiteConfig.save() }),
            ToggleOption("Bone Plating", "Hide bone plating entities.", { noRender.bonePlating }, { noRender.bonePlating = it; SkyLiteConfig.save() }),
            ToggleOption("Healer Fairy", "Hide healer fairy skulls in dungeons.", { noRender.healerFairy }, { noRender.healerFairy = it; SkyLiteConfig.save() }),
            ToggleOption("Tree Bits", "Hide foraging tree block displays.", { noRender.treeBits }, { noRender.treeBits = it; SkyLiteConfig.save() }),
            ToggleOption("Nausea", "Hide nausea overlay.", { noRender.nausea }, { noRender.nausea = it; SkyLiteConfig.save() }),
            ToggleOption("Exp Orbs", "Hide experience orbs.", { noRender.expOrbs }, { noRender.expOrbs = it; SkyLiteConfig.save() })
        )
        register(GuiCategory.GENERAL, noRenderOption)

        val partyCommands = SkyLiteConfig.instance.partyCommands
        val partyOption = ToggleOption(
            "Party Commands",
            "Lets party members run warp, transfer and more.",
            { partyCommands.enabled },
            { partyCommands.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("party", "commands", "warp", "transfer", "coords", "kick", "queue")
        partyOption.children = listOf(
            ToggleOption("Allow Self", "Respond to your own party messages.", { partyCommands.self }, { partyCommands.self = it; SkyLiteConfig.save() }),
            ToggleOption("Warp", "Enable !warp.", { partyCommands.warp }, { partyCommands.warp = it; SkyLiteConfig.save() }),
            ToggleOption("Transfer", "Enable !pt / !ptme.", { partyCommands.transfer }, { partyCommands.transfer = it; SkyLiteConfig.save() }),
            ToggleOption("All Invite", "Enable !allinv.", { partyCommands.allInvite }, { partyCommands.allInvite = it; SkyLiteConfig.save() }),
            ToggleOption("Coords", "Enable !coords.", { partyCommands.coords }, { partyCommands.coords = it; SkyLiteConfig.save() }),
            ToggleOption("Kick", "Enable !kick.", { partyCommands.kick }, { partyCommands.kick = it; SkyLiteConfig.save() }),
            ToggleOption("Queue", "Enable floor/instance queue commands.", { partyCommands.queue }, { partyCommands.queue = it; SkyLiteConfig.save() }),
            ToggleOption("Downtime", "Enable !dt.", { partyCommands.downtime }, { partyCommands.downtime = it; SkyLiteConfig.save() })
        )
        register(GuiCategory.GENERAL, partyOption)

        val slotBinding = SkyLiteConfig.instance.slotBinding
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Slot Binding",
                "Bind hotbar slots to inventory slots.",
                { slotBinding.enabled },
                { slotBinding.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("slot", "bind", "hotbar", "swap", "neu", "inventory")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(SlotBindingScreen(screen))
                }
        )

        val itemProtection = SkyLiteConfig.instance.itemProtection
        val itemProtectionOption = ToggleOption(
            "Item Protection",
            "Blocks dropping or selling protected items. Hold Left Alt to override.",
            { itemProtection.enabled },
            { itemProtection.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("protect", "drop", "salvage", "sell", "starred", "recomb")
        itemProtectionOption.children = listOf(
            ToggleOption("Protect Starred", "Protect items with stars.", { itemProtection.protectStarred }, { itemProtection.protectStarred = it; SkyLiteConfig.save() }),
            ToggleOption("Protect Recombobulated", "Protect recombed items.", { itemProtection.protectRecomb }, { itemProtection.protectRecomb = it; SkyLiteConfig.save() }),
            ToggleOption("Protect Max Quality", "Protect 50/50 dungeon items.", { itemProtection.protectMaxQuality }, { itemProtection.protectMaxQuality = it; SkyLiteConfig.save() }),
            ToggleOption("Protect By Value", "Protect high value items.", { itemProtection.protectValue }, { itemProtection.protectValue = it; SkyLiteConfig.save() }),
            SliderOption("Minimum Value", "Value threshold for Protect By Value.", 100_000f, 100_000_000f, 100_000f, { itemProtection.protectValueMin.toFloat() }, { itemProtection.protectValueMin = it.toDouble(); SkyLiteConfig.save() })
        )
        register(GuiCategory.GENERAL, itemProtectionOption)

        val infoTooltips = SkyLiteConfig.instance.infoTooltips
        val infoTooltipsOption = ToggleOption(
            "Info Tooltips",
            "Adds extra item details to tooltips.",
            { infoTooltips.enabled },
            { infoTooltips.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("tooltip", "quality", "timestamp", "hex", "id", "info")
        infoTooltipsOption.children = listOf(
            ToggleOption("Dungeon Quality", "Show item quality and tier.", { infoTooltips.dungeonQuality }, { infoTooltips.dungeonQuality = it; SkyLiteConfig.save() }),
            ToggleOption("Created Date", "Show creation timestamp.", { infoTooltips.createdDate }, { infoTooltips.createdDate = it; SkyLiteConfig.save() }),
            ToggleOption("Hex Color", "Show dyed armor hex.", { infoTooltips.hexColor }, { infoTooltips.hexColor = it; SkyLiteConfig.save() }),
            ToggleOption("Skyblock ID", "Show the item id.", { infoTooltips.skyblockId }, { infoTooltips.skyblockId = it; SkyLiteConfig.save() })
        )
        register(GuiCategory.GENERAL, infoTooltipsOption)

        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Skill Tracker",
                "Tracks skill XP gain rates on the HUD.",
                { SkyLiteConfig.instance.skillTracker.enabled },
                { SkyLiteConfig.instance.skillTracker.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("skill", "xp", "tracker", "exp", "hud", "sbe")
        )

        val chatTweaks = SkyLiteConfig.instance.chatTweaks
        val chatTweaksOption = ToggleOption(
            "Chat Tweaks",
            "Longer chat history and right-click copy.",
            { chatTweaks.enabled },
            { chatTweaks.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("chat", "copy", "history", "lines", "clipboard")
        chatTweaksOption.children = listOf(
            ToggleOption("Keep History", "Do not clear chat on world change.", { chatTweaks.keepHistory }, { chatTweaks.keepHistory = it; SkyLiteConfig.save() }),
            ToggleOption("Extra Lines", "Raise the chat message buffer.", { chatTweaks.extraLines }, { chatTweaks.extraLines = it; SkyLiteConfig.save() }),
            SliderOption("Line Limit", "Max stored chat lines.", 100f, 5000f, 50f, { chatTweaks.lineLimit.toFloat() }, { chatTweaks.lineLimit = it.toInt(); SkyLiteConfig.save() }),
            ToggleOption("Copy on Right Click", "Copy hovered chat on right click.", { chatTweaks.copyOnRightClick }, { chatTweaks.copyOnRightClick = it; SkyLiteConfig.save() }),
            ToggleOption("Trim on Copy", "Trim whitespace when copying.", { chatTweaks.trimOnCopy }, { chatTweaks.trimOnCopy = it; SkyLiteConfig.save() }),
            ToggleOption("Message on Copy", "Confirm copies in chat.", { chatTweaks.msgOnCopy }, { chatTweaks.msgOnCopy = it; SkyLiteConfig.save() })
        )
        register(GuiCategory.GENERAL, chatTweaksOption)

        val commandKeybinds = SkyLiteConfig.instance.commandKeybinds
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Command Keybinds",
                "Run custom commands from keybinds.",
                { commandKeybinds.enabled },
                { commandKeybinds.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("keybind", "command", "macro", "hotkey")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(CommandKeybindsScreen(screen))
                }
        )

        val chatRules = SkyLiteConfig.instance.chatRules
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Chat Rules",
                "Match chat and show titles or play sounds.",
                { chatRules.enabled },
                { chatRules.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("chat", "rule", "filter", "title", "sound", "regex")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(ChatRulesScreen(screen))
                }
        )

        val commandShortcuts = SkyLiteConfig.instance.commandShortcuts
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Command Shortcuts",
                "Register short client aliases for commands.",
                { commandShortcuts.enabled },
                { commandShortcuts.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("shortcut", "alias", "command", "warp")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(CommandShortcutsScreen(screen))
                }
        )

        val itemAnim = SkyLiteConfig.instance.itemScaleAnimation
        register(
            GuiCategory.VISUALS,
            ToggleOption(
                "Item Scale & Animation",
                "First person held item size, position, rotation and swing.",
                { itemAnim.enabled },
                { itemAnim.enabled = it; SkyLiteConfig.save() }
            ).withKeywords(
                "viewmodel", "view model", "hand", "held", "item", "scale", "size",
                "animation", "swing", "position", "rotation", "odin", "nofrills", "edit"
            ).withEditor { screen ->
                Minecraft.getInstance().setScreenAndShow(ItemScaleEditorScreen(screen))
            }
        )

        val abilityAlert = SkyLiteConfig.instance.abilityAlert
        val abilityOption = ToggleOption(
            "Ability Alert",
            "Alerts you when your Pickaxe Ability is available.",
            { abilityAlert.enabled },
            { abilityAlert.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("pickaxe", "ability", "mining", "cooldown", "drill", "gauntlet")
        abilityOption.children = listOf(
            SliderOption(
                "Override Ticks",
                "Force cooldown length in ticks. 0 uses the item lore.",
                0f, 6000f, 20f,
                { abilityAlert.overrideTicks.toFloat() },
                { abilityAlert.overrideTicks = it.toInt(); SkyLiteConfig.save() }
            ).withKeywords("cooldown", "ticks", "override", "duration")
        )
        register(GuiCategory.MINING, abilityOption)

        val corpse = SkyLiteConfig.instance.corpseHighlight
        val corpseOption = ToggleOption(
            "Corpse Highlight",
            "Highlights corpses in the Glacite Mineshafts.",
            { corpse.enabled },
            { corpse.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("corpse", "mineshaft", "glacite", "lapis", "tungsten", "umber", "vanguard")
        corpseOption.children = listOf(
            ToggleOption(
                "Hide Opened",
                "Stops highlighting corpses you have already opened.",
                { corpse.hideOpened },
                { corpse.hideOpened = it; SkyLiteConfig.save() }
            ).withKeywords("opened", "looted", "hide")
        )
        register(GuiCategory.MINING, corpseOption)

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Ghost Vision",
                "Makes Ghosts easier to see in the Dwarven Mines.",
                { SkyLiteConfig.instance.ghostVision.enabled },
                { SkyLiteConfig.instance.ghostVision.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("ghost", "creeper", "dwarven", "esp", "highlight")
        )

        val scatha = SkyLiteConfig.instance.scathaMining
        val scathaOption = ToggleOption(
            "Scatha Mining",
            "Scatha mining features.",
            { scatha.enabled },
            { scatha.enabled = it; SkyLiteConfig.save() }
        ).withKeywords("scatha", "worm", "crystal", "hollows", "spawn")
        scathaOption.children = listOf(
            ToggleOption(
                "Spawn Alert",
                "Title and sound when a Scatha or Worm spawns nearby.",
                { scatha.alert },
                { scatha.alert = it; SkyLiteConfig.save() }
            ).withKeywords("alert", "spawn", "title"),
            ToggleOption(
                "Cooldown Alert",
                "Alerts when the worm spawn cooldown ends.",
                { scatha.cooldown },
                { scatha.cooldown = it; SkyLiteConfig.save() }
            ).withKeywords("cooldown", "timer")
        )
        register(GuiCategory.MINING, scathaOption)

        register(
            GuiCategory.MINING,
            ToggleOption(
                "End Node Highlight",
                "Highlights End Nodes.",
                { SkyLiteConfig.instance.endNodeHighlight.enabled },
                { SkyLiteConfig.instance.endNodeHighlight.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("end", "node", "terracotta", "zealot")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Gemstone Desync Fix",
                "Fixes adjacent gemstone blocks not correctly updating when mining.",
                { SkyLiteConfig.instance.gemstoneDesyncFix.enabled },
                { SkyLiteConfig.instance.gemstoneDesyncFix.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("gemstone", "desync", "glass", "pane", "visual")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Break Reset Fix",
                "Fixes item updates resetting your block breaking progress.",
                { SkyLiteConfig.instance.breakResetFix.enabled },
                { SkyLiteConfig.instance.breakResetFix.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("hsm", "break", "reset", "mining", "progress")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Shaft Announce",
                "Sends a party chat with the mineshaft ID and corpses on entry.",
                { SkyLiteConfig.instance.shaftAnnounce.enabled },
                { SkyLiteConfig.instance.shaftAnnounce.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("mineshaft", "announce", "party", "corpse", "id")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Commission Highlight",
                "Highlights completed commissions in the commissions menu.",
                { SkyLiteConfig.instance.commissionHighlight.enabled },
                { SkyLiteConfig.instance.commissionHighlight.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("commission", "completed", "menu", "gui")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Commissions Display",
                "Shows active mining commissions and progress on the HUD.",
                { SkyLiteConfig.instance.commissionsDisplay.enabled },
                { SkyLiteConfig.instance.commissionsDisplay.enabled = it; SkyLiteConfig.save() }
            ).withKeywords("commission", "hud", "progress", "display", "percent")
        )

        register(
            GuiCategory.DUNGEONS,
            transientOption("Secret Waypoints", "Marks room secrets while you run.")
                .withKeywords("secrets", "catacombs", "route", "dungeon", "esp")
        )
        register(
            GuiCategory.DUNGEONS,
            transientOption("Terminal Solver", "Solves F7 and M7 device terminals.")
                .withKeywords("f7", "m7", "device", "terms", "necron", "puzzle")
        )
        register(
            GuiCategory.DUNGEONS,
            transientOption("Croesus Helper", "Highlights unopened chests in Croesus.")
                .withKeywords("chest", "loot", "rewards", "unopened")
        )

        register(
            GuiCategory.ECONOMY,
            transientOption("Bazaar Prices", "Shows live buy and sell orders in tooltips.")
                .withKeywords("bz", "market", "price", "tooltip", "buy", "sell", "order")
        )
        register(
            GuiCategory.ECONOMY,
            transientOption("Auction Flips", "Flags underpriced auction listings.")
                .withKeywords("ah", "auction", "flip", "profit", "snipe", "money")
        )
        register(
            GuiCategory.ECONOMY,
            transientOption("Networth", "Estimates the value of your profile.")
                .withKeywords("nw", "value", "coins", "worth", "profile", "money")
        )

        register(
            GuiCategory.WAYPOINTS,
            transientOption("Fairy Souls", "Waypoints for every uncollected soul.")
                .withKeywords("souls", "fairy", "collect", "marker")
        )
        register(
            GuiCategory.WAYPOINTS,
            transientOption("Mining Nodes", "Marks commissions and gemstone spots.")
                .withKeywords("gemstone", "commission", "dwarven", "crystal", "ore", "marker")
        )
    }

    private fun transientOption(title: String, description: String) = ToggleOption(
        title,
        description,
        { transient[title] ?: false },
        { transient[title] = it }
    )

    fun register(category: GuiCategory, option: GuiOption) {
        options.getOrPut(category) { mutableListOf() }.add(option)
    }

    fun optionsFor(category: GuiCategory): List<GuiOption> {
        build()
        return options[category].orEmpty()
    }
}
