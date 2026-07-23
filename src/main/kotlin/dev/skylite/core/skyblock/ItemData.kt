package dev.skylite.core.skyblock

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import dev.skylite.mixin.client.CustomDataAccessor
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.Optional

object ItemData {

    fun customData(stack: ItemStack): CompoundTag {
        if (stack.isEmpty) return CompoundTag()
        val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        return (data as CustomDataAccessor).tag
    }

    fun skyblockId(stack: ItemStack): String =
        customData(stack).getStringOr("id", "")

    fun loreLines(stack: ItemStack): List<String> {
        val lore = stack.get(DataComponents.LORE) ?: return emptyList()
        return lore.lines().map { plain(it).trim() }
    }

    fun plain(text: Component?): String {
        if (text == null) return ""
        return text.string.replace(FORMATTING, "")
    }

    fun hasTexturePayload(stack: ItemStack, payloadHash: Int): Boolean {
        val profile = profile(stack) ?: return false
        return texturePayload(profile).map { it.hashCode() == payloadHash }.orElse(false)
    }

    private fun profile(stack: ItemStack): GameProfile? =
        stack.get(DataComponents.PROFILE)?.partialProfile()

    private fun texturePayload(profile: GameProfile): Optional<String> {
        for (property in profile.properties().values()) {
            if (property is Property && property.name() == "textures") {
                return Optional.of(property.value())
            }
        }
        return Optional.empty()
    }

    fun isMiningTool(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val lines = loreLines(stack)
        if (lines.isEmpty()) return false
        val last = lines.last()
        if (!last.contains(" DRILL") && !last.contains(" PICKAXE") && !last.contains(" GAUNTLET")) {
            return false
        }
        return lines.any { it.startsWith("Mining Speed:") }
    }

    fun rightClickAbility(stack: ItemStack): String {
        for (line in loreLines(stack)) {
            if (line.contains("Ability: ") && line.endsWith("RIGHT CLICK")) {
                return line
            }
        }
        return ""
    }

    fun abilityName(stack: ItemStack): String {
        val ability = rightClickAbility(stack)
        if (ability.isEmpty()) return ""
        return ability.substringAfter(":").replace("RIGHT CLICK", "").trim()
    }

    fun cooldownSeconds(stack: ItemStack): Int? {
        for (line in loreLines(stack).asReversed()) {
            if (line.startsWith("Cooldown: ")) {
                val raw = line.substringAfter(": ").removeSuffix("s")
                return raw.toIntOrNull()
            }
        }
        return null
    }

    private val FORMATTING = Regex("(?i)§[0-9A-FK-OR]")
}
