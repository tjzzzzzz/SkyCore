package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.render.WorldLabels
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object ClassNametags {

    private const val HEALER = 0xFFECB50C.toInt()
    private const val MAGE = 0xFF1793C4.toInt()
    private const val BERS = 0xFFE7413C.toInt()
    private const val ARCH = 0xFF4A14B7.toInt()
    private const val TANK = 0xFF768F46.toInt()
    private const val BASE_SCALE = 0.05f

    fun init() {
        WorldLabels.onRender { _, partial ->
            if (!active()) return@onRender
            val self = Minecraft.getInstance().player ?: return@onRender
            for (player in DungeonUtil.teammateEntities()) {
                if (player === self) continue
                val name = player.gameProfile.name
                val dungeonClass = DungeonUtil.playerClass(name)
                if (dungeonClass.isEmpty()) continue
                val color = when (dungeonClass) {
                    "Healer" -> HEALER
                    "Mage" -> MAGE
                    "Berserk" -> BERS
                    "Archer" -> ARCH
                    "Tank" -> TANK
                    else -> null
                } ?: continue
                val pos = player.getPosition(partial).add(0.0, 3.25, 0.0)
                val text = Component.literal("[${dungeonClass[0]}] ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal(name))
                WorldLabels.distanceScaled(pos, text, BASE_SCALE, color)
            }
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.classNametags.enabled &&
            DungeonUtil.inDungeons() &&
            DungeonUtil.dungeonStarted()
}
