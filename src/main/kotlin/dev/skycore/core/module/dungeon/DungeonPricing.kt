package dev.skycore.core.module.dungeon

import dev.skycore.SkyCore
import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.net.SkyCoreHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean

object DungeonPricing {

    data class BazaarPrice(val buy: Double, val sell: Double)
    data class NpcPrice(val coin: Double, val mote: Double)

    private const val URL = "https://whatyouth.ing/api/nofrills/v2/economy/get-item-pricing/"
    private const val REFRESH_TICKS = 1200

    private val lootAreas = setOf("Catacombs", "Kuudra", "Dungeon Hub", "Crimson Isle")
    private val npcSellItems = setOf(
        "STORM_THE_FISH",
        "MAXOR_THE_FISH",
        "GOLDOR_THE_FISH",
        "DUNGEON_DISC_1",
        "DUNGEON_DISC_2",
        "DUNGEON_DISC_3",
        "DUNGEON_DISC_4",
        "DUNGEON_DISC_5"
    )
    private val quantityPattern = Regex(".* x[0-9]*")
    private val refreshing = AtomicBoolean(false)

    @Volatile
    var auction: Map<String, Long> = emptyMap()
        private set

    @Volatile
    var bazaar: Map<String, BazaarPrice> = emptyMap()
        private set

    @Volatile
    var npc: Map<String, NpcPrice> = emptyMap()
        private set

    private var tickCounter = 0
    private var hooked = false

    fun init() {
        if (hooked) return
        hooked = true
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> refresh() }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!pricingNeeded()) return@register
            tickCounter++
            if (tickCounter >= REFRESH_TICKS) {
                tickCounter = 0
                refresh()
            }
        }
    }

    fun inLootArea(): Boolean = lootAreas.any { TabListCache.isInArea(it) }

    fun lootValue(id: String): Double {
        if (id.isEmpty()) return 0.0
        if (id in npcSellItems) {
            return npc[id]?.coin ?: 0.0
        }
        auction[id]?.let { return it.toDouble() }
        bazaar[id]?.let { return it.sell }
        return 0.0
    }

    fun bazaarBuy(id: String): Double = bazaar[id]?.buy ?: 0.0

    fun formatSeparator(value: Double): String =
        String.format(Locale.ENGLISH, "%,.1f", value)

    fun hasItemQuantity(name: String): Boolean = quantityPattern.matches(name)

    fun marketId(text: Component): String {
        var name = ItemData.plain(text).trim()
        if (hasItemQuantity(name)) {
            name = name.substring(0, name.lastIndexOf(' ')).trim()
        }
        if (name.startsWith("Enchanted Book (") && name.endsWith(")")) {
            val enchant = name.substring(name.indexOf('(') + 1, name.indexOf(')'))
            val space = enchant.lastIndexOf(' ')
            if (space <= 0) return ""
            val enchantName = toId(enchant.substring(0, space))
            val level = parseRoman(enchant.substring(space + 1))
            val style = findStyle(text) { it == enchant }
            if (style != null && hasColor(style, ChatFormatting.LIGHT_PURPLE) && !enchantName.startsWith("ULTIMATE_")) {
                return "ENCHANTMENT_ULTIMATE_${enchantName}_$level"
            }
            return "ENCHANTMENT_${enchantName}_$level"
        }
        if (name.endsWith(" Essence")) {
            return "ESSENCE_${toId(name.substring(0, name.lastIndexOf(' ')))}"
        }
        if (name.endsWith(" Dye")) {
            return "DYE_${toId(name.substring(0, name.lastIndexOf(' ')))}"
        }
        if (name.startsWith("Master Skull - Tier ")) {
            return toId(name.replace(" - ", " "))
        }
        if (name.startsWith("[Lvl 1] ")) {
            val petName = name.substring(name.indexOf(']') + 2)
            val style = findStyle(text) { it == petName }
            var rarity = "COMMON"
            if (style != null) {
                if (hasColor(style, ChatFormatting.GOLD)) rarity = "LEGENDARY"
                if (hasColor(style, ChatFormatting.DARK_PURPLE)) rarity = "EPIC"
                if (hasColor(style, ChatFormatting.BLUE)) rarity = "RARE"
                if (hasColor(style, ChatFormatting.GREEN)) rarity = "UNCOMMON"
            }
            return "${toId(petName)}_PET_$rarity"
        }
        return when (name) {
            "Shadow Warp" -> "SHADOW_WARP_SCROLL"
            "Wither Shield" -> "WITHER_SHIELD_SCROLL"
            "Implosion" -> "IMPLOSION_SCROLL"
            "Giant's Sword" -> "GIANTS_SWORD"
            "Warped Stone" -> "AOTE_STONE"
            "Spirit Boots" -> "THORNS_BOOTS"
            "Spirit Shortbow" -> "ITEM_SPIRIT_BOW"
            "Spirit Stone" -> "SPIRIT_DECOY"
            "Adaptive Blade" -> "STONE_BLADE"
            "Wither Cloak Sword" -> "WITHER_CLOAK"
            "Dungeon Disc" -> "DUNGEON_DISC_1"
            "Clown Disc" -> "DUNGEON_DISC_2"
            "Watcher Disc" -> "DUNGEON_DISC_3"
            "Old Disc" -> "DUNGEON_DISC_4"
            "Necron Disc" -> "DUNGEON_DISC_5"
            "Shiny Wither Helmet" -> "WITHER_HELMET"
            "Shiny Wither Chestplate" -> "WITHER_CHESTPLATE"
            "Shiny Wither Leggings" -> "WITHER_LEGGINGS"
            "Shiny Wither Boots" -> "WITHER_BOOTS"
            "Shiny Necron's Handle" -> "NECRON_HANDLE"
            else -> toId(name)
        }
    }

    fun marketId(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        val data = ItemData.customData(stack)
        val id = data.getStringOr("id", "")
        if (id.isEmpty()) return ""
        if (data.getIntOr("baseStatBoostPercentage", 0) == 50) {
            return "${id}_MAX_BOOST_TIER_${data.getIntOr("item_tier", 0)}"
        }
        when (id) {
            "PET" -> {
                val petInfo = data.getStringOr("petInfo", "")
                if (petInfo.isNotEmpty()) {
                    val obj = runCatching {
                        SkyCoreHttp.json.parseToJsonElement(petInfo).jsonObject
                    }.getOrNull()
                    if (obj != null) {
                        val type = obj["type"]?.jsonPrimitive?.content ?: return "UNKNOWN_PET"
                        val tier = obj["tier"]?.jsonPrimitive?.content ?: return "UNKNOWN_PET"
                        return "${type}_PET_$tier"
                    }
                }
                return "UNKNOWN_PET"
            }
            "RUNE", "UNIQUE_RUNE" -> {
                val runeData = data.getCompound("runes").orElse(null) ?: return "EMPTY_RUNE"
                val keys = runeData.keySet()
                if (keys.isEmpty()) return "EMPTY_RUNE"
                val runeId = keys.first()
                return "${runeId}_${runeData.getIntOr(runeId, 0)}_RUNE"
            }
            "ENCHANTED_BOOK" -> {
                val enchantData = data.getCompound("enchantments").orElse(null) ?: return "ENCHANTMENT_UNKNOWN"
                val keys = enchantData.keySet()
                if (keys.size != 1) return "ENCHANTMENT_UNKNOWN"
                val enchantId = keys.first()
                return "ENCHANTMENT_${enchantId.uppercase(Locale.ROOT)}_${enchantData.getIntOr(enchantId, 0)}"
            }
            "POTION" -> {
                val potion = data.getStringOr("potion", "")
                if (potion.isEmpty()) return "UNKNOWN_POTION"
                return "${potion.uppercase(Locale.ROOT)}_${data.getIntOr("potion_level", 0)}_POTION"
            }
        }
        return id
    }

    fun lootId(stack: ItemStack, name: String): String {
        if (name.startsWith("Wither Essence")) return "ESSENCE_WITHER"
        if (name.startsWith("Undead Essence")) return "ESSENCE_UNDEAD"
        return marketId(stack)
    }

    fun lootQuantity(stack: ItemStack, name: String): Int {
        val parts = name.split(' ')
        val last = parts.lastOrNull() ?: return stack.count
        if (last.startsWith("x")) {
            return last.removePrefix("x").replace(",", "").toIntOrNull() ?: stack.count
        }
        return stack.count
    }

    fun loreComponents(stack: ItemStack): List<Component> {
        val lore = stack.get(DataComponents.LORE) ?: return emptyList()
        return lore.lines()
    }

    fun findStyle(text: Component, predicate: (String) -> Boolean): Style? {
        var found: Style? = null
        text.visit({ style, str ->
            if (predicate(str)) {
                found = style
                Optional.of(true)
            } else {
                Optional.empty()
            }
        }, Style.EMPTY)
        return found
    }

    fun hasColor(style: Style, formatting: ChatFormatting): Boolean {
        val hex = TextColor.fromLegacyFormat(formatting)?.value ?: return false
        return style.color?.value == hex
    }

    fun parseRoman(roman: String): Int {
        var result = 0
        for (i in roman.indices) {
            val number = romanValue(roman[i])
            if (number == 0) return 0
            if (i != roman.lastIndex) {
                val next = romanValue(roman[i + 1])
                if (number < next) result -= number else result += number
            } else {
                result += number
            }
        }
        return result
    }

    fun refresh() {
        if (!refreshing.compareAndSet(false, true)) return
        SkyCore.scope.launch {
            try {
                val body = SkyCoreHttp.instance.get(URL).bodyAsText()
                val root = SkyCoreHttp.json.parseToJsonElement(body).jsonObject
                val nextAuction = HashMap<String, Long>()
                root["auction"]?.jsonObject?.forEach { (key, value) ->
                    value.jsonPrimitive.longOrNull?.let { nextAuction[key] = it }
                }
                val nextBazaar = HashMap<String, BazaarPrice>()
                root["bazaar"]?.jsonObject?.forEach { (key, value) ->
                    val obj = value.jsonObject
                    val buy = obj["buy"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val sell = obj["sell"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    nextBazaar[key] = BazaarPrice(buy, sell)
                }
                val nextNpc = HashMap<String, NpcPrice>()
                root["npc"]?.jsonObject?.forEach { (key, value) ->
                    val obj = value.jsonObject
                    val coin = obj["coin"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val mote = obj["mote"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    nextNpc[key] = NpcPrice(coin, mote)
                }
                auction = nextAuction
                bazaar = nextBazaar
                npc = nextNpc
            } catch (t: Throwable) {
                SkyCore.logger.error("Failed to refresh dungeon item pricing", t)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun pricingNeeded(): Boolean {
        if (!SkyCoreConfig.instance.enabled) return false
        val cfg = SkyCoreConfig.instance
        return cfg.croesusSolver.enabled || cfg.dungeonChestValue.enabled
    }

    private fun toId(string: String): String =
        string.replace("'s", "").replace(' ', '_').uppercase(Locale.ROOT)

    private fun romanValue(c: Char): Int = when (c.uppercaseChar()) {
        'I' -> 1
        'V' -> 5
        'X' -> 10
        'L' -> 50
        'C' -> 100
        'D' -> 500
        'M' -> 1000
        else -> 0
    }
}
