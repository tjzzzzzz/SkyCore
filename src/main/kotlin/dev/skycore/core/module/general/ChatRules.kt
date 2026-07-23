package dev.skycore.core.module.general

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import java.util.regex.PatternSyntaxException

enum class MatchType {
    Equals,
    Contains,
    StartsWith,
    EndsWith,
    Regex
}

object ChatRules {

    fun init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay || !enabled()) return@register true
            val plain = ItemData.plain(message)
            for (rule in rules()) {
                if (!matches(rule, plain)) continue
                apply(rule)
                return@register !rule.cancel
            }
            true
        }
    }

    fun rules(): MutableList<SkyCoreConfig.ChatRuleEntry> =
        SkyCoreConfig.instance.chatRules.rules

    fun save() {
        SkyCoreConfig.save()
    }

    private fun enabled(): Boolean =
        SkyCoreConfig.instance.enabled && SkyCoreConfig.instance.chatRules.enabled

    private fun matchTypeOf(rule: SkyCoreConfig.ChatRuleEntry): MatchType =
        MatchType.entries.firstOrNull { it.name.equals(rule.matchType, ignoreCase = true) }
            ?: MatchType.Equals

    private fun matches(rule: SkyCoreConfig.ChatRuleEntry, msg: String): Boolean {
        if (!rule.enabled || rule.match.isEmpty()) return false
        if (rule.islandFilter.isNotBlank()) {
            val area = TabListCache.area
            if (area.isNotEmpty()) {
                val lower = area.lowercase()
                val ok = rule.islandFilter.lowercase().split(Regex("\\s+"))
                    .any { it.isNotEmpty() && lower.contains(it) }
                if (!ok) return false
            }
        }
        val needle = if (rule.caseSensitive) rule.match else rule.match.lowercase()
        val hay = if (rule.caseSensitive) msg else msg.lowercase()
        return when (matchTypeOf(rule)) {
            MatchType.Equals -> hay == needle
            MatchType.Contains -> hay.contains(needle)
            MatchType.StartsWith -> hay.startsWith(needle)
            MatchType.EndsWith -> hay.endsWith(needle)
            MatchType.Regex -> try {
                val flags = if (rule.caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
                Regex(rule.match, flags).matches(msg)
            } catch (_: PatternSyntaxException) {
                Titles.error("Invalid regex in chat rule: ${rule.name}")
                false
            }
        }
    }

    private fun apply(rule: SkyCoreConfig.ChatRuleEntry) {
        if (rule.title.isNotEmpty()) {
            Titles.show(
                rule.title.replace('&', '§'),
                fadeIn = rule.titleFadeIn,
                stay = rule.titleStay,
                fadeOut = rule.titleFadeOut
            )
        }
        if (rule.sound.isNotEmpty()) {
            val id = Identifier.tryParse(rule.sound) ?: return
            Titles.play(SoundEvent.createVariableRangeEvent(id), rule.soundVolume, rule.soundPitch)
        }
    }
}
