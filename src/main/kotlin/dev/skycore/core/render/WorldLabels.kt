package dev.skycore.core.render

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList

object WorldLabels {

    private const val FULL_BRIGHT = 0xF000F0

    private data class Label(
        val pos: Vec3,
        val text: Component,
        val scale: Float,
        val color: Int,
        val throughWalls: Boolean
    )

    private val listeners = CopyOnWriteArrayList<(CameraRenderState, Float) -> Unit>()
    private val labels = ArrayList<Label>()
    private var camera: CameraRenderState? = null
    private var hooked = false

    fun init() {
        if (hooked) return
        hooked = true
        LevelRenderEvents.END_MAIN.register { context ->
            camera = context.levelState().cameraRenderState
            val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
            labels.clear()
            for (listener in listeners) listener(camera!!, partial)
            if (labels.isNotEmpty()) {
                flush(context.poseStack(), context.submitNodeCollector(), camera!!)
            }
            camera = null
        }
    }

    fun onRender(listener: (CameraRenderState, Float) -> Unit) {
        init()
        listeners += listener
    }

    fun text(pos: Vec3, text: Component, scale: Float, color: Int = 0xFFFFFFFF.toInt(), throughWalls: Boolean = true) {
        labels += Label(pos, text, scale, color, throughWalls)
    }

    fun text(pos: Vec3, text: String, scale: Float, color: Int = 0xFFFFFFFF.toInt(), throughWalls: Boolean = true) {
        text(pos, Component.literal(text), scale, color, throughWalls)
    }

    fun distanceScaled(
        pos: Vec3,
        text: Component,
        baseScale: Float,
        color: Int = 0xFFFFFFFF.toInt(),
        throughWalls: Boolean = true,
        scaling: Float = 0.1f
    ) {
        val cam = camera ?: return
        val dist = cam.pos.distanceTo(pos)
        val distScale = (1.0 + dist * scaling).toFloat()
        val scale = maxOf(baseScale * distScale, baseScale)
        text(pos.add(0.0, dist * baseScale, 0.0), text, scale, color, throughWalls)
    }

    private fun flush(
        poseStack: PoseStack,
        collector: net.minecraft.client.renderer.SubmitNodeCollector,
        cam: CameraRenderState
    ) {
        val font = Minecraft.getInstance().font
        val inverse = Quaternionf(cam.orientation).conjugate()
        for (label in labels) {
            poseStack.pushPose()
            poseStack.translate(
                label.pos.x - cam.pos.x,
                label.pos.y - cam.pos.y,
                label.pos.z - cam.pos.z
            )
            poseStack.mulPose(inverse)
            poseStack.scale(label.scale, -label.scale, label.scale)
            val sequence: FormattedCharSequence = label.text.visualOrderText
            val mode = if (label.throughWalls) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
            val width = font.width(sequence)
            collector.submitText(
                poseStack,
                -width / 2f,
                0f,
                sequence,
                false,
                mode,
                FULL_BRIGHT,
                label.color,
                0,
                0
            )
            poseStack.popPose()
        }
        labels.clear()
    }
}
