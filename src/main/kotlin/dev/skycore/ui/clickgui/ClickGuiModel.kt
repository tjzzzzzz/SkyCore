package dev.skycore.ui.clickgui

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.keybind.SkyCoreKeys
import dev.skycore.core.module.LegacyPackInstaller
import dev.skycore.core.module.ServerPackControl
import dev.skycore.ui.general.ChatRulesScreen
import dev.skycore.ui.general.CommandKeybindsScreen
import dev.skycore.ui.general.CommandShortcutsScreen
import dev.skycore.ui.general.SlotBindingScreen
import dev.skycore.ui.render.Fonts
import dev.skycore.ui.visuals.ItemScaleEditorScreen
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

enum class GuiCategory(val displayName: String) {
    GENERAL("General"),
    VISUALS("Visuals"),
    MINING("Mining"),
    DUNGEONS("Dungeons"),
    ECONOMY("Economy"),
    WAYPOINTS("Waypoints");

    val label: Component = Fonts.label(displayName, Fonts.MEDIUM)
}

sealed class GuiOption(val title: String, val description: String) {
    var hover: Float = 0f

    val titleLabel: Component = Fonts.label(title, Fonts.MEDIUM)
    val descLabel: Component = Fonts.label(description, Fonts.SMALL)

    var children: List<GuiOption> = emptyList()
    var expanded: Boolean = false

    val hasChildren: Boolean get() = children.isNotEmpty()

    var keywords: List<String> = emptyList()
}

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

    var knob: Float = if (getter()) 1f else 0f

    var editAction: ((Screen) -> Unit)? = null

    fun toggle() {
        enabled = !enabled
    }
}

fun ToggleOption.withEditor(action: (Screen) -> Unit): ToggleOption {
    editAction = action
    return this
}

class KeybindOption(
    title: String,
    description: String,
    val mapping: KeyMapping
) : GuiOption(title, description) {

    val boundKeyLabel: String get() = SkyCoreKeys.labelOf(mapping)
}

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

object ClickGuiRegistry {

    private val options = LinkedHashMap<GuiCategory, MutableList<GuiOption>>()

    private val transient = HashMap<String, Boolean>()

    private var built = false

    private fun build() {
        if (built) return
        built = true

        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Enable SkyCore",
                "Master switch for every module.",
                { SkyCoreConfig.instance.enabled },
                { SkyCoreConfig.instance.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("master", "toggle", "on", "off", "disable", "enable")
        )
        register(
            GuiCategory.GENERAL,
            KeybindOption(
                "Open Menu",
                "Optional hotkey. /skycore always works. Delete unbinds.",
                SkyCoreKeys.openClickGui
            ).withKeywords("keybind", "key", "bind", "hotkey", "shortcut", "gui", "menu", "command")
        )
        val infoBar = SkyCoreConfig.instance.infoBar
        val infoBarOption = ToggleOption(
            "Info Bar",
            "FPS, ping, server TPS and lobby day.",
            { infoBar.enabled },
            { infoBar.enabled = it; SkyCoreConfig.save() }
        )
        infoBarOption.withKeywords("hud", "overlay", "stats", "counter", "fps", "ping", "tps", "day", "performance")
        infoBarOption.children = listOf(
            ToggleOption("FPS", "Client frame rate.", { infoBar.fps }, { infoBar.fps = it; SkyCoreConfig.save() })
                .withKeywords("framerate", "frames", "performance", "lag", "smooth"),
            ToggleOption("Ping", "Round trip to the server.", { infoBar.ping }, { infoBar.ping = it; SkyCoreConfig.save() })
                .withKeywords("latency", "ms", "lag", "connection", "network"),
            ToggleOption("Server TPS", "Measured from server time sync.", { infoBar.tps }, { infoBar.tps = it; SkyCoreConfig.save() })
                .withKeywords("ticks", "tickrate", "lag", "server", "performance"),
            ToggleOption("Day", "In game day of the lobby.", { infoBar.day }, { infoBar.day = it; SkyCoreConfig.save() })
                .withKeywords("lobby", "age", "time", "server")
        )
        register(GuiCategory.GENERAL, infoBarOption)

        val sprint = SkyCoreConfig.instance.toggleSprint
        val sprintOption = ToggleOption(
            "Toggle Sprint",
            "Keeps you sprinting without holding the key.",
            { sprint.enabled },
            { sprint.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("sprint", "run", "auto", "toggle", "speed", "movement", "autosprint")
        sprintOption.children = listOf(
            ToggleOption(
                "Show HUD", "Displays a SPRINT status pill.",
                { sprint.showHud }, { sprint.showHud = it; SkyCoreConfig.save() }
            ).withKeywords("hud", "overlay", "indicator", "pill", "status")
        )
        register(GuiCategory.GENERAL, sprintOption)

        val serverPack = SkyCoreConfig.instance.serverPack
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Legacy Textures",
                "Layers the legacy pack over Hypixel's, so old textures win.",
                { serverPack.disable },
                {
                    serverPack.disable = it
                    SkyCoreConfig.save()
                    if (it) {

                        LegacyPackInstaller.ensureInstalled(serverPack.autoEnableLegacy)
                    } else {

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

        val fullbright = SkyCoreConfig.instance.fullbright
        val fullbrightOption = ToggleOption(
            "Fullbright",
            "Brightens the world so caves stay readable.",
            { fullbright.enabled },
            { fullbright.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("fullbright", "gamma", "night", "vision", "brightness", "light")
        fullbrightOption.children = listOf(
            ToggleOption(
                "Ambient Mode",
                "Force full ambient lighting.",
                { fullbright.mode.equals("Ambient", true) },
                { if (it) { fullbright.mode = "Ambient"; SkyCoreConfig.save() } }
            ),
            ToggleOption(
                "Gamma Mode",
                "Push lightmap gamma extremely high.",
                { fullbright.mode.equals("Gamma", true) },
                { if (it) { fullbright.mode = "Gamma"; SkyCoreConfig.save() } }
            ),
            ToggleOption(
                "Potion Mode",
                "Apply night vision continuously.",
                { fullbright.mode.equals("Potion", true) },
                { if (it) { fullbright.mode = "Potion"; SkyCoreConfig.save() } }
            ),
            ToggleOption(
                "Clear Night Vision",
                "Remove night vision when not in Potion mode.",
                { fullbright.noEffect },
                { fullbright.noEffect = it; SkyCoreConfig.save() }
            )
        )
        register(GuiCategory.GENERAL, fullbrightOption)

        val chatWaypoints = SkyCoreConfig.instance.chatWaypoints
        val chatWaypointsOption = ToggleOption(
            "Chat Waypoints",
            "Creates waypoints from coordinates in chat.",
            { chatWaypoints.enabled },
            { chatWaypoints.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("waypoint", "coords", "coordinates", "party", "chat", "beam")
        chatWaypointsOption.children = listOf(
            ToggleOption("Party Chat", "Parse coordinates from party chat.", { chatWaypoints.partyEnabled }, { chatWaypoints.partyEnabled = it; SkyCoreConfig.save() }),
            SliderOption("Party Duration", "Seconds party waypoints last.", 10f, 600f, 10f, { chatWaypoints.partyDuration.toFloat() }, { chatWaypoints.partyDuration = it.toInt(); SkyCoreConfig.save() }),
            ToggleOption("Party Clear on Arrive", "Remove party waypoints when you get close.", { chatWaypoints.partyClearOnArrive }, { chatWaypoints.partyClearOnArrive = it; SkyCoreConfig.save() }),
            ToggleOption("All Chat", "Parse coordinates from public chat.", { chatWaypoints.allEnabled }, { chatWaypoints.allEnabled = it; SkyCoreConfig.save() }),
            SliderOption("All Duration", "Seconds public waypoints last.", 10f, 600f, 10f, { chatWaypoints.allDuration.toFloat() }, { chatWaypoints.allDuration = it.toInt(); SkyCoreConfig.save() }),
            ToggleOption("All Clear on Arrive", "Remove public waypoints when you get close.", { chatWaypoints.allClearOnArrive }, { chatWaypoints.allClearOnArrive = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.GENERAL, chatWaypointsOption)

        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Etherwarp Overlay",
                "Highlights the block targeted by Ether Transmission.",
                { SkyCoreConfig.instance.etherwarpOverlay.enabled },
                { SkyCoreConfig.instance.etherwarpOverlay.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("etherwarp", "ether", "transmission", "aotv", "aote", "overlay")
        )

        val noRender = SkyCoreConfig.instance.noRender
        val noRenderOption = ToggleOption(
            "No Render",
            "Hides selected particles, entities and HUD clutter.",
            { noRender.enabled },
            { noRender.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("norender", "hide", "particles", "fog", "bossbar", "clean")
        noRenderOption.children = listOf(
            ToggleOption("Explosions", "Hide explosion particles.", { noRender.explosions }, { noRender.explosions = it; SkyCoreConfig.save() }),
            ToggleOption("Fire Overlay", "Hide first person fire.", { noRender.fireOverlay }, { noRender.fireOverlay = it; SkyCoreConfig.save() }),
            ToggleOption("Break Particles", "Hide block break particles.", { noRender.breakParticles }, { noRender.breakParticles = it; SkyCoreConfig.save() }),
            ToggleOption("Boss Bar", "Hide the boss health bar.", { noRender.bossBar }, { noRender.bossBar = it; SkyCoreConfig.save() }),
            ToggleOption("Armor Bar", "Hide armor HUD.", { noRender.armorBar }, { noRender.armorBar = it; SkyCoreConfig.save() }),
            ToggleOption("Food Bar", "Hide hunger HUD.", { noRender.foodBar }, { noRender.foodBar = it; SkyCoreConfig.save() }),
            ToggleOption("Fog", "Remove distance fog.", { noRender.fog }, { noRender.fog = it; SkyCoreConfig.save() }),
            ToggleOption("Effect Display", "Hide potion icons.", { noRender.effectDisplay }, { noRender.effectDisplay = it; SkyCoreConfig.save() }),
            ToggleOption("Recipe Book", "Hide inventory recipe book.", { noRender.recipeBook }, { noRender.recipeBook = it; SkyCoreConfig.save() }),
            ToggleOption("Selected Item Name", "Hide hotbar item name.", { noRender.selectedItemName }, { noRender.selectedItemName = it; SkyCoreConfig.save() }),
            ToggleOption("Empty Tooltips", "Disable blank GUI tooltips.", { noRender.emptyTooltips }, { noRender.emptyTooltips = it; SkyCoreConfig.save() }),
            ToggleOption("Dead Entities", "Hide dead mobs.", { noRender.deadEntities }, { noRender.deadEntities = it; SkyCoreConfig.save() }),
            ToggleOption("Dead Poof", "Hide death poof particles.", { noRender.deadPoof }, { noRender.deadPoof = it; SkyCoreConfig.save() }),
            ToggleOption("Lightning", "Hide lightning bolts.", { noRender.lightning }, { noRender.lightning = it; SkyCoreConfig.save() }),
            ToggleOption("Falling Blocks", "Hide falling block entities.", { noRender.fallingBlocks }, { noRender.fallingBlocks = it; SkyCoreConfig.save() }),
            ToggleOption("Entity Fire", "Hide fire on entities.", { noRender.entityFire }, { noRender.entityFire = it; SkyCoreConfig.save() }),
            ToggleOption("Mage Beam", "Hide mage beam fireworks in dungeons.", { noRender.mageBeam }, { noRender.mageBeam = it; SkyCoreConfig.save() }),
            ToggleOption("Ice Spray", "Hide ice spray particles.", { noRender.iceSpray }, { noRender.iceSpray = it; SkyCoreConfig.save() }),
            ToggleOption("Powder Coating", "Hide powder coating dust.", { noRender.powderCoating }, { noRender.powderCoating = it; SkyCoreConfig.save() }),
            ToggleOption("Soulweaver Skulls", "Hide soulweaver skulls in dungeons.", { noRender.soulweaverSkulls }, { noRender.soulweaverSkulls = it; SkyCoreConfig.save() }),
            ToggleOption("Guided Sheep", "Hide guided sheep in dungeons.", { noRender.guidedSheep }, { noRender.guidedSheep = it; SkyCoreConfig.save() }),
            ToggleOption("Bone Plating", "Hide bone plating entities.", { noRender.bonePlating }, { noRender.bonePlating = it; SkyCoreConfig.save() }),
            ToggleOption("Healer Fairy", "Hide healer fairy skulls in dungeons.", { noRender.healerFairy }, { noRender.healerFairy = it; SkyCoreConfig.save() }),
            ToggleOption("Tree Bits", "Hide foraging tree block displays.", { noRender.treeBits }, { noRender.treeBits = it; SkyCoreConfig.save() }),
            ToggleOption("Nausea", "Hide nausea overlay.", { noRender.nausea }, { noRender.nausea = it; SkyCoreConfig.save() }),
            ToggleOption("Exp Orbs", "Hide experience orbs.", { noRender.expOrbs }, { noRender.expOrbs = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.GENERAL, noRenderOption)

        val partyCommands = SkyCoreConfig.instance.partyCommands
        val partyOption = ToggleOption(
            "Party Commands",
            "Lets party members run warp, transfer and more.",
            { partyCommands.enabled },
            { partyCommands.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("party", "commands", "warp", "transfer", "coords", "kick", "queue")
        partyOption.children = listOf(
            ToggleOption("Allow Self", "Respond to your own party messages.", { partyCommands.self }, { partyCommands.self = it; SkyCoreConfig.save() }),
            ToggleOption("Warp", "Enable !warp.", { partyCommands.warp }, { partyCommands.warp = it; SkyCoreConfig.save() }),
            ToggleOption("Transfer", "Enable !pt / !ptme.", { partyCommands.transfer }, { partyCommands.transfer = it; SkyCoreConfig.save() }),
            ToggleOption("All Invite", "Enable !allinv.", { partyCommands.allInvite }, { partyCommands.allInvite = it; SkyCoreConfig.save() }),
            ToggleOption("Coords", "Enable !coords.", { partyCommands.coords }, { partyCommands.coords = it; SkyCoreConfig.save() }),
            ToggleOption("Kick", "Enable !kick.", { partyCommands.kick }, { partyCommands.kick = it; SkyCoreConfig.save() }),
            ToggleOption("Queue", "Enable floor/instance queue commands.", { partyCommands.queue }, { partyCommands.queue = it; SkyCoreConfig.save() }),
            ToggleOption("Downtime", "Enable !dt.", { partyCommands.downtime }, { partyCommands.downtime = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.GENERAL, partyOption)

        val slotBinding = SkyCoreConfig.instance.slotBinding
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Slot Binding",
                "Bind hotbar slots to inventory slots.",
                { slotBinding.enabled },
                { slotBinding.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("slot", "bind", "hotbar", "swap", "neu", "inventory")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(SlotBindingScreen(screen))
                }
        )

        val itemProtection = SkyCoreConfig.instance.itemProtection
        val itemProtectionOption = ToggleOption(
            "Item Protection",
            "Blocks dropping or selling protected items. Hold Left Alt to override.",
            { itemProtection.enabled },
            { itemProtection.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("protect", "drop", "salvage", "sell", "starred", "recomb")
        itemProtectionOption.children = listOf(
            ToggleOption("Protect Starred", "Protect items with stars.", { itemProtection.protectStarred }, { itemProtection.protectStarred = it; SkyCoreConfig.save() }),
            ToggleOption("Protect Recombobulated", "Protect recombed items.", { itemProtection.protectRecomb }, { itemProtection.protectRecomb = it; SkyCoreConfig.save() }),
            ToggleOption("Protect Max Quality", "Protect 50/50 dungeon items.", { itemProtection.protectMaxQuality }, { itemProtection.protectMaxQuality = it; SkyCoreConfig.save() }),
            ToggleOption("Protect By Value", "Protect high value items.", { itemProtection.protectValue }, { itemProtection.protectValue = it; SkyCoreConfig.save() }),
            SliderOption("Minimum Value", "Value threshold for Protect By Value.", 100_000f, 100_000_000f, 100_000f, { itemProtection.protectValueMin.toFloat() }, { itemProtection.protectValueMin = it.toDouble(); SkyCoreConfig.save() })
        )
        register(GuiCategory.GENERAL, itemProtectionOption)

        val infoTooltips = SkyCoreConfig.instance.infoTooltips
        val infoTooltipsOption = ToggleOption(
            "Info Tooltips",
            "Adds extra item details to tooltips.",
            { infoTooltips.enabled },
            { infoTooltips.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("tooltip", "quality", "timestamp", "hex", "id", "info")
        infoTooltipsOption.children = listOf(
            ToggleOption("Dungeon Quality", "Show item quality and tier.", { infoTooltips.dungeonQuality }, { infoTooltips.dungeonQuality = it; SkyCoreConfig.save() }),
            ToggleOption("Created Date", "Show creation timestamp.", { infoTooltips.createdDate }, { infoTooltips.createdDate = it; SkyCoreConfig.save() }),
            ToggleOption("Hex Color", "Show dyed armor hex.", { infoTooltips.hexColor }, { infoTooltips.hexColor = it; SkyCoreConfig.save() }),
            ToggleOption("Skyblock ID", "Show the item id.", { infoTooltips.skyblockId }, { infoTooltips.skyblockId = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.GENERAL, infoTooltipsOption)

        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Skill Tracker",
                "Shows the skill you are currently gaining XP for. Hides after 5s idle.",
                { SkyCoreConfig.instance.skillTracker.enabled },
                { SkyCoreConfig.instance.skillTracker.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("skill", "xp", "tracker", "exp", "hud", "sbe")
        )

        val chatTweaks = SkyCoreConfig.instance.chatTweaks
        val chatTweaksOption = ToggleOption(
            "Chat Tweaks",
            "Longer chat history and right-click copy.",
            { chatTweaks.enabled },
            { chatTweaks.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("chat", "copy", "history", "lines", "clipboard")
        chatTweaksOption.children = listOf(
            ToggleOption("Keep History", "Do not clear chat on world change.", { chatTweaks.keepHistory }, { chatTweaks.keepHistory = it; SkyCoreConfig.save() }),
            ToggleOption("Extra Lines", "Raise the chat message buffer.", { chatTweaks.extraLines }, { chatTweaks.extraLines = it; SkyCoreConfig.save() }),
            SliderOption("Line Limit", "Max stored chat lines.", 100f, 5000f, 50f, { chatTweaks.lineLimit.toFloat() }, { chatTweaks.lineLimit = it.toInt(); SkyCoreConfig.save() }),
            ToggleOption("Copy on Right Click", "Copy hovered chat on right click.", { chatTweaks.copyOnRightClick }, { chatTweaks.copyOnRightClick = it; SkyCoreConfig.save() }),
            ToggleOption("Trim on Copy", "Trim whitespace when copying.", { chatTweaks.trimOnCopy }, { chatTweaks.trimOnCopy = it; SkyCoreConfig.save() }),
            ToggleOption("Message on Copy", "Confirm copies in chat.", { chatTweaks.msgOnCopy }, { chatTweaks.msgOnCopy = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.GENERAL, chatTweaksOption)

        val commandKeybinds = SkyCoreConfig.instance.commandKeybinds
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Command Keybinds",
                "Run custom commands from keybinds.",
                { commandKeybinds.enabled },
                { commandKeybinds.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("keybind", "command", "macro", "hotkey")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(CommandKeybindsScreen(screen))
                }
        )

        val chatRules = SkyCoreConfig.instance.chatRules
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Chat Rules",
                "Match chat and show titles or play sounds.",
                { chatRules.enabled },
                { chatRules.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("chat", "rule", "filter", "title", "sound", "regex")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(ChatRulesScreen(screen))
                }
        )

        val commandShortcuts = SkyCoreConfig.instance.commandShortcuts
        register(
            GuiCategory.GENERAL,
            ToggleOption(
                "Command Shortcuts",
                "Register short client aliases for commands.",
                { commandShortcuts.enabled },
                { commandShortcuts.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("shortcut", "alias", "command", "warp")
                .withEditor { screen ->
                    Minecraft.getInstance().setScreenAndShow(CommandShortcutsScreen(screen))
                }
        )

        val itemAnim = SkyCoreConfig.instance.itemScaleAnimation
        register(
            GuiCategory.VISUALS,
            ToggleOption(
                "Item Scale & Animation",
                "First person held item size, position, rotation and swing.",
                { itemAnim.enabled },
                { itemAnim.enabled = it; SkyCoreConfig.save() }
            ).withKeywords(
                "viewmodel", "view model", "hand", "held", "item", "scale", "size",
                "animation", "swing", "position", "rotation", "odin", "nofrills", "edit"
            ).withEditor { screen ->
                Minecraft.getInstance().setScreenAndShow(ItemScaleEditorScreen(screen))
            }
        )

        val abilityAlert = SkyCoreConfig.instance.abilityAlert
        register(
            GuiCategory.MINING,
            ToggleOption(
                "Ability Alert",
                "Title alert when chat says your pickaxe ability is available.",
                { abilityAlert.enabled },
                { abilityAlert.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("pickaxe", "ability", "mining", "cooldown", "drill", "gauntlet", "speed boost")
        )

        val corpse = SkyCoreConfig.instance.corpseHighlight
        val corpseOption = ToggleOption(
            "Corpse Highlight",
            "Highlights corpses in the Glacite Mineshafts.",
            { corpse.enabled },
            { corpse.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("corpse", "mineshaft", "glacite", "lapis", "tungsten", "umber", "vanguard")
        corpseOption.children = listOf(
            ToggleOption(
                "Hide Opened",
                "Stops highlighting corpses you have already opened.",
                { corpse.hideOpened },
                { corpse.hideOpened = it; SkyCoreConfig.save() }
            ).withKeywords("opened", "looted", "hide")
        )
        register(GuiCategory.MINING, corpseOption)

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Ghost Vision",
                "Makes Ghosts easier to see in the Dwarven Mines.",
                { SkyCoreConfig.instance.ghostVision.enabled },
                { SkyCoreConfig.instance.ghostVision.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("ghost", "creeper", "dwarven", "esp", "highlight")
        )

        val scatha = SkyCoreConfig.instance.scathaMining
        val scathaOption = ToggleOption(
            "Scatha Mining",
            "Scatha mining features.",
            { scatha.enabled },
            { scatha.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("scatha", "worm", "crystal", "hollows", "spawn")
        scathaOption.children = listOf(
            ToggleOption(
                "Spawn Alert",
                "Title and sound when a Scatha or Worm spawns nearby.",
                { scatha.alert },
                { scatha.alert = it; SkyCoreConfig.save() }
            ).withKeywords("alert", "spawn", "title"),
            ToggleOption(
                "Cooldown Alert",
                "Alerts when the worm spawn cooldown ends.",
                { scatha.cooldown },
                { scatha.cooldown = it; SkyCoreConfig.save() }
            ).withKeywords("cooldown", "timer")
        )
        register(GuiCategory.MINING, scathaOption)

        register(
            GuiCategory.MINING,
            ToggleOption(
                "End Node Highlight",
                "Highlights End Nodes.",
                { SkyCoreConfig.instance.endNodeHighlight.enabled },
                { SkyCoreConfig.instance.endNodeHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("end", "node", "terracotta", "zealot")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Gemstone Desync Fix",
                "Fixes adjacent gemstone blocks not correctly updating when mining.",
                { SkyCoreConfig.instance.gemstoneDesyncFix.enabled },
                { SkyCoreConfig.instance.gemstoneDesyncFix.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("gemstone", "desync", "glass", "pane", "visual")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Break Reset Fix",
                "Fixes item updates resetting your block breaking progress.",
                { SkyCoreConfig.instance.breakResetFix.enabled },
                { SkyCoreConfig.instance.breakResetFix.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("hsm", "break", "reset", "mining", "progress")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Shaft Announce",
                "Sends a party chat with the mineshaft ID and corpses on entry.",
                { SkyCoreConfig.instance.shaftAnnounce.enabled },
                { SkyCoreConfig.instance.shaftAnnounce.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("mineshaft", "announce", "party", "corpse", "id")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Commission Highlight",
                "Highlights completed commissions in the commissions menu.",
                { SkyCoreConfig.instance.commissionHighlight.enabled },
                { SkyCoreConfig.instance.commissionHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("commission", "completed", "menu", "gui")
        )

        register(
            GuiCategory.MINING,
            ToggleOption(
                "Commissions Display",
                "Shows active mining commissions and progress on the HUD.",
                { SkyCoreConfig.instance.commissionsDisplay.enabled },
                { SkyCoreConfig.instance.commissionsDisplay.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("commission", "hud", "progress", "display", "percent")
        )

        val terminalSolvers = SkyCoreConfig.instance.terminalSolvers
        val terminalOption = ToggleOption(
            "Terminal Solvers",
            "Highlights and blocks wrong clicks on F7/M7 terminals.",
            { terminalSolvers.enabled },
            { terminalSolvers.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("f7", "m7", "terminal", "panes", "melody", "device", "necron")
        terminalOption.children = listOf(
            ToggleOption("Correct Panes", "Solve Correct all the panes.", { terminalSolvers.panes }, { terminalSolvers.panes = it; SkyCoreConfig.save() }),
            ToggleOption("Starts With", "Solve What starts with terminals.", { terminalSolvers.startsWith }, { terminalSolvers.startsWith = it; SkyCoreConfig.save() }),
            ToggleOption("Select All", "Solve Select all the color items.", { terminalSolvers.select }, { terminalSolvers.select = it; SkyCoreConfig.save() }),
            ToggleOption("Click in Order", "Solve Click in order terminals.", { terminalSolvers.inOrder }, { terminalSolvers.inOrder = it; SkyCoreConfig.save() }),
            ToggleOption("Draw Order Numbers", "Show numbers on Click in order.", { terminalSolvers.inOrderDrawNumbers }, { terminalSolvers.inOrderDrawNumbers = it; SkyCoreConfig.save() }),
            ToggleOption("Change Colors", "Solve Change all to same color.", { terminalSolvers.colors }, { terminalSolvers.colors = it; SkyCoreConfig.save() }),
            ToggleOption("Click Sound", "Play a sound when clicking solutions.", { terminalSolvers.soundOnClick }, { terminalSolvers.soundOnClick = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, terminalOption)

        val deviceSolvers = SkyCoreConfig.instance.deviceSolvers
        val deviceOption = ToggleOption(
            "Device Solvers",
            "Helps with F7/M7 devices like Arrow Align and Sharpshooter.",
            { deviceSolvers.enabled },
            { deviceSolvers.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("arrow", "align", "sharpshooter", "device", "f7", "m7")
        deviceOption.children = listOf(
            ToggleOption("Arrow Align", "Shows clicks needed for Arrow Align.", { deviceSolvers.arrowAlign }, { deviceSolvers.arrowAlign = it; SkyCoreConfig.save() }),
            ToggleOption("Block Wrong Align", "Prevents clicking finished Arrow Align frames.", { deviceSolvers.alignBlockWrong }, { deviceSolvers.alignBlockWrong = it; SkyCoreConfig.save() }),
            ToggleOption("Invert Align Block", "Invert shift behavior for Arrow Align blocking.", { deviceSolvers.alignBlockInvert }, { deviceSolvers.alignBlockInvert = it; SkyCoreConfig.save() }),
            ToggleOption("Sharpshooter", "Highlights Sharpshooter targets.", { deviceSolvers.sharpshooter }, { deviceSolvers.sharpshooter = it; SkyCoreConfig.save() }),
            ToggleOption("Sharpshooter Done Alert", "Title when Sharpshooter finishes.", { deviceSolvers.sharpDoneAlert }, { deviceSolvers.sharpDoneAlert = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, deviceOption)

        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Leap Overlay",
                "Replaces Spirit Leap with a class-colored overlay.",
                { SkyCoreConfig.instance.leapOverlay.enabled },
                { SkyCoreConfig.instance.leapOverlay.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("leap", "spirit", "overlay", "class")
        )

        val croesus = SkyCoreConfig.instance.croesusSolver
        val croesusOption = ToggleOption(
            "Croesus Solver",
            "Highlights profitable chests and unopened floors in Croesus.",
            { croesus.enabled },
            { croesus.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("croesus", "chest", "loot", "profit", "kismet")
        croesusOption.children = listOf(
            ToggleOption("Value Tooltip", "Show chest value in tooltips.", { croesus.valueTooltip }, { croesus.valueTooltip = it; SkyCoreConfig.save() }),
            ToggleOption("Floor Labels", "Show floor labels on Croesus pages.", { croesus.floorLabel }, { croesus.floorLabel = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, croesusOption)

        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Dungeon Chest Value",
                "Shows estimated profit while opening dungeon reward chests.",
                { SkyCoreConfig.instance.dungeonChestValue.enabled },
                { SkyCoreConfig.instance.dungeonChestValue.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("chest", "value", "profit", "reward")
        )

        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Quick Close",
                "Closes reward chests when pressing movement keys.",
                { SkyCoreConfig.instance.quickClose.enabled },
                { SkyCoreConfig.instance.quickClose.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("close", "chest", "wasd", "quick")
        )

        val melody = SkyCoreConfig.instance.melodyMessage
        val melodyOption = ToggleOption(
            "Melody Message",
            "Announces Melody terminal start and progress.",
            { melody.enabled },
            { melody.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("melody", "terminal", "party", "chat")
        melodyOption.children = listOf(
            ToggleOption("Progress Messages", "Send Melody percent progress.", { melody.progress }, { melody.progress = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, melodyOption)

        val secretChime = SkyCoreConfig.instance.secretChime
        val secretChimeOption = ToggleOption(
            "Secret Chime",
            "Plays sounds for secret items, chests, bats, levers, and essence.",
            { secretChime.enabled },
            { secretChime.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("secret", "chime", "bat", "chest", "essence", "lever")
        secretChimeOption.children = listOf(
            ToggleOption("Items", "Chime when picking up secret items.", { secretChime.itemsToggle }, { secretChime.itemsToggle = it; SkyCoreConfig.save() }),
            ToggleOption("Chests", "Chime when opening secret chests.", { secretChime.chestToggle }, { secretChime.chestToggle = it; SkyCoreConfig.save() }),
            ToggleOption("Essence", "Chime when clicking wither essence.", { secretChime.essenceToggle }, { secretChime.essenceToggle = it; SkyCoreConfig.save() }),
            ToggleOption("Bats", "Chime when secret bats die.", { secretChime.batToggle }, { secretChime.batToggle = it; SkyCoreConfig.save() }),
            ToggleOption("Levers", "Chime when flipping secret levers.", { secretChime.leverToggle }, { secretChime.leverToggle = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, secretChimeOption)

        val witherDragons = SkyCoreConfig.instance.witherDragons
        val witherOption = ToggleOption(
            "Wither Dragons",
            "Tracks M7 wither dragon spawns, boxes, and health.",
            { witherDragons.enabled },
            { witherDragons.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("dragon", "m7", "wither", "split", "ice spray")
        witherOption.children = listOf(
            ToggleOption("Spawn Alert", "Announce priority dragon on split.", { witherDragons.alert }, { witherDragons.alert = it; SkyCoreConfig.save() }),
            ToggleOption("Spawn Boxes", "Outline dragon spawn areas.", { witherDragons.boxes }, { witherDragons.boxes = it; SkyCoreConfig.save() }),
            ToggleOption("Hitboxes", "Outline alive dragon hitboxes.", { witherDragons.hitboxes }, { witherDragons.hitboxes = it; SkyCoreConfig.save() }),
            ToggleOption("Tracers", "Trace to priority spawning dragon.", { witherDragons.tracers }, { witherDragons.tracers = it; SkyCoreConfig.save() }),
            ToggleOption("Spawn Timer", "Show countdown while dragons spawn.", { witherDragons.timer }, { witherDragons.timer = it; SkyCoreConfig.save() }),
            ToggleOption("Health", "Show dragon health labels.", { witherDragons.health }, { witherDragons.health = it; SkyCoreConfig.save() }),
            ToggleOption("Ice Spray Tracker", "Chat when a dragon is ice sprayed.", { witherDragons.trackIceSpray }, { witherDragons.trackIceSpray = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, witherOption)

        val scoreCalc = SkyCoreConfig.instance.scoreCalculator
        val scoreOption = ToggleOption(
            "Score Calculator",
            "Tracks dungeon score and shows it on the HUD.",
            { scoreCalc.enabled },
            { scoreCalc.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("score", "s+", "270", "300", "paul")
        scoreOption.children = listOf(
            ToggleOption("Send 270 Message", "Party chat when reaching 270 score.", { scoreCalc.sendMsg270 }, { scoreCalc.sendMsg270 = it; SkyCoreConfig.save() }),
            ToggleOption("Show 270 Title", "Title when reaching 270 score.", { scoreCalc.showTitle270 }, { scoreCalc.showTitle270 = it; SkyCoreConfig.save() }),
            ToggleOption("Send 300 Message", "Party chat when reaching 300 score.", { scoreCalc.sendMsg300 }, { scoreCalc.sendMsg300 = it; SkyCoreConfig.save() }),
            ToggleOption("Show 300 Title", "Title when reaching 300 score.", { scoreCalc.showTitle300 }, { scoreCalc.showTitle300 = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, scoreOption)

        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Mimic Message",
                "Announces when Mimic is killed.",
                { SkyCoreConfig.instance.mimicMessage.enabled },
                { SkyCoreConfig.instance.mimicMessage.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("mimic", "party", "chat")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Prince Message",
                "Announces when Prince is killed.",
                { SkyCoreConfig.instance.princeMessage.enabled },
                { SkyCoreConfig.instance.princeMessage.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("prince", "party", "chat")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Terracotta Timer",
                "Shows terracotta respawn timer in F6/M6.",
                { SkyCoreConfig.instance.terracottaTimer.enabled },
                { SkyCoreConfig.instance.terracottaTimer.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("terracotta", "gyro", "timer", "f6")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Key Highlight",
                "Highlights wither and blood keys.",
                { SkyCoreConfig.instance.keyHighlight.enabled },
                { SkyCoreConfig.instance.keyHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("key", "wither", "blood", "highlight")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Livid Solver",
                "Highlights the correct Livid on F5/M5.",
                { SkyCoreConfig.instance.lividSolver.enabled },
                { SkyCoreConfig.instance.lividSolver.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("livid", "f5", "m5", "boss")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Secret Bat Highlight",
                "Highlights secret bats in dungeons.",
                { SkyCoreConfig.instance.secretBatHighlight.enabled },
                { SkyCoreConfig.instance.secretBatHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("bat", "secret", "highlight")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Spirit Bow Highlight",
                "Highlights the Spirit Bow on F4/M4.",
                { SkyCoreConfig.instance.spiritBowHighlight.enabled },
                { SkyCoreConfig.instance.spiritBowHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("spirit", "bow", "f4", "m4")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Relic Highlight",
                "Highlights your M7 king relic placement spot.",
                { SkyCoreConfig.instance.relicHighlight.enabled },
                { SkyCoreConfig.instance.relicHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("relic", "m7", "p5", "king")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Class Nametags",
                "Shows class colored nametags for teammates.",
                { SkyCoreConfig.instance.classNametags.enabled },
                { SkyCoreConfig.instance.classNametags.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("class", "nametag", "teammate")
        )
        val platform = SkyCoreConfig.instance.platformHighlight
        val platformOption = ToggleOption(
            "Platform Highlight",
            "Highlights the Healer 3x3 platform after F7/M7 terminals.",
            { platform.enabled },
            { platform.enabled = it; SkyCoreConfig.save() }
        ).withKeywords("platform", "healer", "f7", "m7", "mine")
        platformOption.children = listOf(
            ToggleOption("Healer Only", "Only show platform highlights as Healer.", { platform.healerOnly }, { platform.healerOnly = it; SkyCoreConfig.save() })
        )
        register(GuiCategory.DUNGEONS, platformOption)
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Miniboss Highlight",
                "Highlights dungeon minibosses.",
                { SkyCoreConfig.instance.minibossHighlight.enabled },
                { SkyCoreConfig.instance.minibossHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("miniboss", "highlight", "esp")
        )
        register(
            GuiCategory.DUNGEONS,
            ToggleOption(
                "Starred Mob Highlight",
                "Highlights starred dungeon mobs.",
                { SkyCoreConfig.instance.starredMobHighlight.enabled },
                { SkyCoreConfig.instance.starredMobHighlight.enabled = it; SkyCoreConfig.save() }
            ).withKeywords("starred", "mob", "highlight", "esp")
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
