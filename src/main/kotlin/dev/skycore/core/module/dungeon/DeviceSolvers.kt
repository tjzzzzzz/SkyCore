package dev.skycore.core.module.dungeon

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.dungeon.DungeonEvents
import dev.skycore.core.dungeon.DungeonUtil
import dev.skycore.core.render.WorldBoxes
import dev.skycore.core.render.WorldLabels
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object DeviceSolvers {

    private val opts get() = SkyCoreConfig.instance.deviceSolvers

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!active() || !DungeonUtil.inBossRoom("7")) return@register
            if (opts.arrowAlign) {
                if (ArrowAlign.isActive()) {
                    if (ArrowAlign.solutionMap.isEmpty()) {
                        val frames = ArrowAlign.getFrames()
                        val solution = ArrowAlign.findSolution(frames)
                        if (frames.isEmpty() || solution.isEmpty()) return@register
                        for (frame in frames) {
                            ArrowAlign.solutionMap[frame] = solution[ArrowAlign.toIndex(frame)]
                        }
                    }
                } else if (ArrowAlign.solutionMap.isNotEmpty()) {
                    ArrowAlign.solutionMap.clear()
                    ArrowAlign.clicksMap.clear()
                }
            }
            if (opts.sharpshooter && !Sharpshooter.isActive() && (Sharpshooter.list.isNotEmpty() || Sharpshooter.next != null)) {
                Sharpshooter.next = null
                Sharpshooter.list.clear()
            }
        }

        DungeonEvents.onBlockUpdate { pos, oldState, newState ->
            onBlockUpdate(pos, oldState, newState)
        }
        DungeonEvents.onNamed { entity, _, plain ->
            onEntityNamed(entity, plain)
        }
        DungeonEvents.onUpdated { entity ->
            onEntityUpdated(entity)
        }

        WorldBoxes.onRender { _, _ ->
            if (!active() || !DungeonUtil.inBossRoom("7") || !opts.sharpshooter) return@onRender
            for (pos in Sharpshooter.list) {
                if (pos == Sharpshooter.next) continue
                val box = AABB.encapsulatingFullBlocks(pos, pos)
                WorldBoxes.both(box, opts.sharpHitColorFill, opts.sharpHitColorOutline, throughWalls = false)
            }
            val next = Sharpshooter.next
            if (next != null) {
                val box = AABB.encapsulatingFullBlocks(next, next)
                WorldBoxes.both(box, opts.sharpTargetColorFill, opts.sharpTargetColorOutline, throughWalls = false)
            }
        }

        WorldLabels.onRender { _, _ ->
            if (!active() || !DungeonUtil.inBossRoom("7") || !opts.arrowAlign || !ArrowAlign.isActive()) return@onRender
            for ((frame, target) in ArrowAlign.solutionMap) {
                val pos = frame.eyePosition.add(0.0, 0.2, 0.0)
                val rotation = ArrowAlign.clicksMap[frame] ?: frame.getRotation()
                val clicks = ArrowAlign.neededClicks(rotation, target)
                if (clicks > 0) {
                    WorldLabels.text(pos, Component.literal(clicks.toString()), 0.04f, 0xFFFFFFFF.toInt())
                }
            }
        }
    }

    fun onBlockUpdate(pos: BlockPos, oldState: BlockState, newState: BlockState) {
        if (!active() || !opts.sharpshooter) return
        if (!Sharpshooter.isTargetBlock(pos) || !DungeonUtil.inBossRoom("7") || !Sharpshooter.isActive()) return
        if (oldState.block == Blocks.EMERALD_BLOCK && newState.block == Blocks.DYED_TERRACOTTA.blue()) {
            Sharpshooter.list.add(pos.immutable())
            if (Sharpshooter.next == pos) Sharpshooter.next = null
        } else if (oldState.block == Blocks.DYED_TERRACOTTA.blue() && newState.block == Blocks.EMERALD_BLOCK) {
            Sharpshooter.list.remove(pos)
            Sharpshooter.next = pos.immutable()
        }
    }

    fun onEntityNamed(entity: Entity, plain: String) {
        if (!active() || !opts.sharpshooter || !DungeonUtil.inBossRoom("7")) return
        if (Sharpshooter.done || plain != "Active") return
        if (Vec3.atCenterOf(Sharpshooter.area).distanceTo(entity.position()) >= 3.0) return
        if (opts.sharpDoneAlert) {
            Titles.show("§aSharpshooter Done", stay = 40)
            Titles.playPling()
        }
        Sharpshooter.done = true
    }

    fun shouldCancelEntityInteract(entity: Entity): Boolean {
        if (!active() || !opts.arrowAlign || !DungeonUtil.inBossRoom("7")) return false
        if (entity !is ItemFrame) return false
        val target = ArrowAlign.solutionMap[entity] ?: return false
        val rotation = ArrowAlign.clicksMap[entity] ?: entity.getRotation()
        if (ArrowAlign.shouldBlock() && ArrowAlign.neededClicks(rotation, target) == 0) {
            return true
        }
        ArrowAlign.clicksMap[entity] = (rotation + 1) % 8
        return false
    }

    fun onEntityUpdated(entity: Entity) {
        if (!active() || !opts.arrowAlign || !DungeonUtil.inBossRoom("7")) return
        if (entity !is ItemFrame) return
        if (!ArrowAlign.solutionMap.containsKey(entity)) return
        val expected = ArrowAlign.clicksMap[entity] ?: return
        if (entity.getRotation() == expected) {
            ArrowAlign.clicksMap.remove(entity)
        }
    }

    private fun active(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    private fun reset() {
        Sharpshooter.done = false
        Sharpshooter.next = null
        Sharpshooter.list.clear()
        ArrowAlign.solutionMap.clear()
        ArrowAlign.clicksMap.clear()
    }

    object Sharpshooter {
        val list = ConcurrentHashMap.newKeySet<BlockPos>()
        val target = AABB.encapsulatingFullBlocks(BlockPos(68, 130, 50), BlockPos(64, 126, 50))
        val area = BlockPos(63, 126, 35)

        @Volatile
        var next: BlockPos? = null

        @Volatile
        var done: Boolean = false

        fun isActive(): Boolean {
            if (!opts.sharpshooter) return false
            val level = Minecraft.getInstance().level ?: return false
            return level.hasSignal(area, Direction.DOWN)
        }

        fun isTargetBlock(pos: BlockPos): Boolean =
            target.contains(Vec3.atCenterOf(pos)) && pos.x % 2 == 0 && pos.y % 2 == 0
    }

    object ArrowAlign {
        val area = AABB(-2.0, 125.0, 81.0, 4.0, 120.0, 74.0)
        val corner = BlockPos(-2, 124, 79)
        val solutions = arrayOf(
            intArrayOf(1, 1, 3, -1, -1, 7, -1, 3, -1, -1, -1, -1, 3, -1, -1, -1, -1, 3, -1, 7, -1, -1, 1, 1, 7),
            intArrayOf(1, 1, -1, 5, 5, 7, -1, -1, -1, 7, 7, 5, -1, 1, 7, -1, 7, -1, 7, -1, -1, -1, -1, -1, -1),
            intArrayOf(3, 5, 5, -1, -1, 3, -1, 7, -1, -1, -1, -1, -1, -1, -1, -1, -1, 3, -1, 7, -1, -1, 1, 1, 7),
            intArrayOf(-1, -1, -1, -1, -1, 3, -1, -1, -1, 3, 3, -1, -1, -1, 3, 3, -1, 7, -1, 3, 1, 1, 7, 5, 5),
            intArrayOf(1, 1, 1, 1, 3, 7, -1, -1, -1, 3, 7, -1, -1, -1, 3, 7, -1, 7, -1, 3, -1, -1, 7, 5, 5),
            intArrayOf(1, 1, 3, -1, -1, 7, -1, 3, -1, 7, 7, -1, 3, -1, 7, 7, -1, 3, -1, 7, -1, -1, 1, 1, 7),
            intArrayOf(-1, 1, 1, 1, -1, -1, -1, -1, -1, -1, -1, 1, 1, 1, -1, -1, -1, -1, -1, -1, -1, 1, 1, 1, -1),
            intArrayOf(-1, 1, 1, 3, -1, -1, 7, -1, 3, -1, -1, 7, -1, 3, -1, -1, 7, -1, 3, -1, -1, 7, -1, 1, -1),
            intArrayOf(-1, 1, 3, -1, -1, -1, -1, 1, 1, -1, -1, 1, 7, -1, -1, -1, -1, 1, 1, -1, -1, 1, 7, -1, -1)
        )
        val solutionMap = ConcurrentHashMap<ItemFrame, Int>()
        val clicksMap = ConcurrentHashMap<ItemFrame, Int>()

        fun isActive(): Boolean {
            val player = Minecraft.getInstance().player ?: return false
            return area.getCenter().distanceTo(player.position()) <= 8.0
        }

        fun neededClicks(current: Int, target: Int): Int =
            (8 - current + target) % 8

        fun getFrames(): List<ItemFrame> {
            val level = Minecraft.getInstance().level ?: return emptyList()
            val frames = ArrayList<ItemFrame>()
            for (entity in level.entitiesForRendering()) {
                if (entity !is ItemFrame) continue
                if (!entity.boundingBox.intersects(area)) continue
                if (entity.item.item != Items.ARROW) continue
                frames += entity
            }
            return frames
        }

        fun toIndex(entity: ItemFrame): Int {
            val pos = entity.blockPosition()
            return corner.z - pos.z + 5 * (corner.y - pos.y)
        }

        fun matchSolution(array: IntArray, frames: List<ItemFrame>): Boolean {
            for (frame in frames) {
                if (array[toIndex(frame)] == -1) return false
            }
            var count = 0
            for (value in array) {
                if (value != -1) count++
            }
            return count == frames.size
        }

        fun findSolution(frames: List<ItemFrame>): IntArray {
            if (frames.isEmpty()) return intArrayOf()
            for (array in solutions) {
                if (matchSolution(array, frames)) return array
            }
            return intArrayOf()
        }

        fun shouldBlock(): Boolean {
            if (!opts.alignBlockWrong) return false
            val shift = Minecraft.getInstance().options.keyShift.isDown
            return if (opts.alignBlockInvert) shift else !shift
        }
    }
}
