package dev.skycore.core.module.mining

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.location.IslandType
import dev.skycore.core.location.LocationManager
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.skyblock.ItemData
import dev.skycore.core.skyblock.TabListCache
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

object CorpseHighlight {

    private val stands = ConcurrentHashMap.newKeySet<Int>()
    private val opened = ConcurrentHashMap.newKeySet<Int>()

    private const val LAPIS_FILL = 0x805555FF.toInt()
    private const val LAPIS_OUT = 0xFF5555FF.toInt()
    private const val TUNGSTEN_FILL = 0x80AAAAAA.toInt()
    private const val TUNGSTEN_OUT = 0xFFAAAAAA.toInt()
    private const val UMBER_FILL = 0x80FFAA00.toInt()
    private const val UMBER_OUT = 0xFFFFAA00.toInt()
    private const val VANGUARD_FILL = 0x80FF55FF.toInt()
    private const val VANGUARD_OUT = 0xFFFF55FF.toInt()

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> opened.clear() }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            stands.clear()
            if (!active()) return@register
            val level = client.level ?: return@register
            for (entity in level.entitiesForRendering()) {
                if (entity is ArmorStand && !entity.isInvisible) {
                    stands.add(entity.id)
                }
            }
        }
        WorldBoxes.onRender { _, _ ->
            if (!active()) return@onRender
            val level = Minecraft.getInstance().level ?: return@onRender
            val hideOpened = SkyCoreConfig.instance.corpseHighlight.hideOpened
            for (id in stands) {
                if (hideOpened && opened.contains(id)) continue
                val stand = level.getEntity(id) as? ArmorStand ?: continue
                val type = corpseType(stand)
                if (type == CorpseType.None) continue
                val box = stand.getDimensions(Pose.STANDING).makeBoundingBox(stand.position()).inflate(0.25, 0.0, 0.25)
                when (type) {
                    CorpseType.Lapis -> WorldBoxes.both(box, LAPIS_FILL, LAPIS_OUT)
                    CorpseType.Tungsten -> WorldBoxes.both(box, TUNGSTEN_FILL, TUNGSTEN_OUT)
                    CorpseType.Umber -> WorldBoxes.both(box, UMBER_FILL, UMBER_OUT)
                    CorpseType.Vanguard -> WorldBoxes.both(box, VANGUARD_FILL, VANGUARD_OUT)
                    CorpseType.None -> {}
                }
            }
        }
    }

    fun onInteract(entity: Entity) {
        if (!active() || !SkyCoreConfig.instance.corpseHighlight.hideOpened) return
        val stand = entity as? ArmorStand ?: return
        val type = corpseType(stand)
        if (type != CorpseType.None && hasKey(type)) {
            opened.add(stand.id)
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled &&
            SkyCoreConfig.instance.corpseHighlight.enabled &&
            (LocationManager.current == IslandType.GLACITE_MINESHAFTS || TabListCache.isInArea("Mineshaft"))

    private fun corpseType(stand: ArmorStand): CorpseType {
        val helmet = stand.getItemBySlot(EquipmentSlot.HEAD)
        if (helmet.isEmpty) return CorpseType.None
        return when (ItemData.plain(helmet.hoverName)) {
            "Lapis Armor Helmet" -> CorpseType.Lapis
            "Mineral Helmet" -> CorpseType.Tungsten
            "Yog Helmet" -> CorpseType.Umber
            "Vanguard Helmet" -> CorpseType.Vanguard
            else -> CorpseType.None
        }
    }

    private fun hasKey(type: CorpseType): Boolean {
        val id = when (type) {
            CorpseType.Tungsten -> "TUNGSTEN_KEY"
            CorpseType.Umber -> "UMBER_KEY"
            CorpseType.Vanguard -> "SKELETON_KEY"
            else -> return true
        }
        val inv = Minecraft.getInstance().player?.inventory ?: return false
        for (i in 0..35) {
            val stack: ItemStack = inv.getItem(i)
            if (!stack.isEmpty && ItemData.skyblockId(stack) == id) return true
        }
        return false
    }

    private enum class CorpseType { Lapis, Tungsten, Umber, Vanguard, None }
}
