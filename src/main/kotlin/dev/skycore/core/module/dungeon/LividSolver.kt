package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.dungeon.EntityCache
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

object LividSolver {

    private const val OUTLINE = 0xFF00FF00.toInt()
    private const val FILL = 0x8000FF00.toInt()

    private val lividData: Map<Block, Livid> = buildLividData()
    private val lividNames = lividData.values.map { it.name }.toHashSet()
    private val cache = EntityCache.create()

    @Volatile
    private var currentName = ""

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> currentName = "" }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> currentName = "" }

        DungeonEvents.onBlockUpdate { pos, _, newState ->
            if (!active()) return@onBlockUpdate
            if (pos.y !in 107..110) return@onBlockUpdate
            val livid = lividData[newState.block] ?: return@onBlockUpdate
            if (currentName == livid.name) return@onBlockUpdate
            currentName = livid.name
            val hud = Minecraft.getInstance().gui.hud
            hud.setTimes(0, 50, 10)
            hud.setTitle(
                Component.literal("${livid.title}!")
                    .withStyle(Style.EMPTY.withBold(true).withColor(livid.color))
            )
            Titles.playOrb()
        }

        DungeonEvents.onUpdated { entity ->
            if (!active()) return@onUpdated
            if (entity !is Player || DungeonUtil.isRealPlayer(entity)) return@onUpdated
            if (cache.has(entity)) return@onUpdated
            val name = ItemData.plain(entity.name)
            if (name in lividNames) cache.add(entity)
        }

        WorldBoxes.onRender { _, partial ->
            if (!active() || cache.size() <= 1 || currentName.isEmpty()) return@onRender
            for (livid in cache.get()) {
                if (ItemData.plain(livid.name) != currentName) continue
                WorldBoxes.both(lerpedBox(livid, partial), FILL, OUTLINE, throughWalls = false)
                break
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.lividSolver.enabled &&
            DungeonUtil.inBossRoom("5")

    private fun buildLividData(): Map<Block, Livid> {
        val list = listOf(
            Livid("Hockey Livid", "RED", ChatFormatting.RED, Blocks.WOOL.red(), Blocks.STAINED_GLASS.red()),
            Livid("Arcade Livid", "YELLOW", ChatFormatting.YELLOW, Blocks.WOOL.yellow(), Blocks.STAINED_GLASS.yellow()),
            Livid("Smile Livid", "GREEN", ChatFormatting.GREEN, Blocks.WOOL.lime(), Blocks.STAINED_GLASS.lime()),
            Livid("Frog Livid", "DARK GREEN", ChatFormatting.DARK_GREEN, Blocks.WOOL.green(), Blocks.STAINED_GLASS.green()),
            Livid("Scream Livid", "BLUE", ChatFormatting.BLUE, Blocks.WOOL.blue(), Blocks.STAINED_GLASS.blue()),
            Livid("Crossed Livid", "PINK", ChatFormatting.LIGHT_PURPLE, Blocks.WOOL.magenta(), Blocks.STAINED_GLASS.magenta()),
            Livid("Purple Livid", "PURPLE", ChatFormatting.DARK_PURPLE, Blocks.WOOL.purple(), Blocks.STAINED_GLASS.purple()),
            Livid("Doctor Livid", "GRAY", ChatFormatting.GRAY, Blocks.WOOL.gray(), Blocks.STAINED_GLASS.gray()),
            Livid("Vendetta Livid", "WHITE", ChatFormatting.WHITE, Blocks.WOOL.white(), Blocks.STAINED_GLASS.white())
        )
        val map = HashMap<Block, Livid>(list.size * 2)
        for (livid in list) {
            map[livid.wool] = livid
            map[livid.glass] = livid
        }
        return map
    }

    private fun lerpedBox(entity: Entity, partial: Float): AABB {
        val x = Mth.lerp(partial.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(partial.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(partial.toDouble(), entity.zOld, entity.z)
        return entity.boundingBox.move(x - entity.x, y - entity.y, z - entity.z)
    }

    private data class Livid(
        val name: String,
        val title: String,
        val color: ChatFormatting,
        val wool: Block,
        val glass: Block
    )
}
