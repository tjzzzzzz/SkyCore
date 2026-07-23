package dev.skycore.core.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector4f
import java.util.ArrayList
import java.util.Optional
import java.util.OptionalDouble
import java.util.concurrent.CopyOnWriteArrayList

object WorldBoxes {

    private val FILLED_THROUGH: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skycore", "pipeline/filled_through"))
            .withDepthStencilState(Optional.empty())
            .build()
    )

    private val LINES_THROUGH: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skycore", "pipeline/lines_through"))
            .withDepthStencilState(Optional.empty())
            .build()
    )

    private val vertexBuffer = StagedVertexBuffer({ "SkyCore WorldBoxes" }, RenderType.SMALL_BUFFER_SIZE)
    private val draws = ArrayList<Draw>()
    private var previousDraw: StagedVertexBuffer.Draw? = null
    private var previousPipeline: RenderPipeline? = null
    private var camera: CameraRenderState? = null

    private val listeners = CopyOnWriteArrayList<(CameraRenderState, Float) -> Unit>()

    fun init() {
        LevelRenderEvents.START_MAIN.register {
            previousDraw = null
            previousPipeline = null
            draws.clear()
        }
        LevelRenderEvents.END_MAIN.register { context ->
            camera = context.levelState().cameraRenderState
            val partial = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
            for (listener in listeners) {
                listener(camera!!, partial)
            }
            flush()
        }
    }

    fun onRender(listener: (CameraRenderState, Float) -> Unit) {
        listeners += listener
    }

    fun filled(box: AABB, color: Int, throughWalls: Boolean = true) {
        val cam = camera ?: return
        val buffer = buffer(if (throughWalls) FILLED_THROUGH else RenderPipelines.DEBUG_FILLED_BOX)
        val matrix = Matrix4f().translate((-cam.pos.x).toFloat(), (-cam.pos.y).toFloat(), (-cam.pos.z).toFloat())
        val minX = box.minX.toFloat()
        val minY = box.minY.toFloat()
        val minZ = box.minZ.toFloat()
        val maxX = box.maxX.toFloat()
        val maxY = box.maxY.toFloat()
        val maxZ = box.maxZ.toFloat()
        val a = (color ushr 24 and 0xFF) / 255f
        val r = (color ushr 16 and 0xFF) / 255f
        val g = (color ushr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        quad(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a)
        quad(buffer, matrix, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a)
        quad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a)
        quad(buffer, matrix, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a)
        quad(buffer, matrix, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a)
        quad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a)
    }

    fun outline(box: AABB, color: Int, throughWalls: Boolean = true, width: Float = 2.5f) {
        val cam = camera ?: return
        val buffer = buffer(if (throughWalls) LINES_THROUGH else RenderPipelines.LINES)
        val matrix = Matrix4f().translate((-cam.pos.x).toFloat(), (-cam.pos.y).toFloat(), (-cam.pos.z).toFloat())
        val minX = box.minX.toFloat()
        val minY = box.minY.toFloat()
        val minZ = box.minZ.toFloat()
        val maxX = box.maxX.toFloat()
        val maxY = box.maxY.toFloat()
        val maxZ = box.maxZ.toFloat()
        val a = (color ushr 24 and 0xFF) / 255f
        val r = (color ushr 16 and 0xFF) / 255f
        val g = (color ushr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        edge(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, 1f, 0f, 0f, r, g, b, a, width)
        edge(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, 0f, 1f, 0f, r, g, b, a, width)
        edge(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, 0f, 0f, 1f, r, g, b, a, width)
        edge(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, 0f, 1f, 0f, r, g, b, a, width)
        edge(buffer, matrix, maxX, maxY, minZ, minX, maxY, minZ, -1f, 0f, 0f, r, g, b, a, width)
        edge(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ, 0f, 0f, 1f, r, g, b, a, width)
        edge(buffer, matrix, minX, maxY, maxZ, minX, minY, maxZ, 0f, -1f, 0f, r, g, b, a, width)
        edge(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ, 1f, 0f, 0f, r, g, b, a, width)
        edge(buffer, matrix, maxX, minY, maxZ, maxX, minY, minZ, 0f, 0f, -1f, r, g, b, a, width)
        edge(buffer, matrix, minX, maxY, maxZ, maxX, maxY, maxZ, 1f, 0f, 0f, r, g, b, a, width)
        edge(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, 0f, 1f, 0f, r, g, b, a, width)
        edge(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, 0f, 0f, 1f, r, g, b, a, width)
    }

    fun both(box: AABB, fill: Int, outline: Int, throughWalls: Boolean = true) {
        filled(box, fill, throughWalls)
        outline(box, outline, throughWalls)
    }

    fun line(from: net.minecraft.world.phys.Vec3, to: net.minecraft.world.phys.Vec3, color: Int, width: Float = 4f) {
        val cam = camera ?: return
        val buffer = buffer(LINES_THROUGH)
        val matrix = Matrix4f().translate((-cam.pos.x).toFloat(), (-cam.pos.y).toFloat(), (-cam.pos.z).toFloat())
        val a = (color ushr 24 and 0xFF) / 255f
        val r = (color ushr 16 and 0xFF) / 255f
        val g = (color ushr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val dx = (to.x - from.x).toFloat()
        val dy = (to.y - from.y).toFloat()
        val dz = (to.z - from.z).toFloat()
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
        edge(
            buffer, matrix,
            from.x.toFloat(), from.y.toFloat(), from.z.toFloat(),
            to.x.toFloat(), to.y.toFloat(), to.z.toFloat(),
            dx / len, dy / len, dz / len,
            r, g, b, a, width
        )
    }

    fun tracer(pos: net.minecraft.world.phys.Vec3, color: Int, width: Float = 4f) {
        val cam = camera ?: return
        line(cam.pos, pos, color, width)
    }

    private fun buffer(pipeline: RenderPipeline): VertexConsumer {
        if (previousDraw == null || pipeline !== previousPipeline) {
            val format = requireNotNull(pipeline.getVertexFormatBinding(0))
            previousDraw = vertexBuffer.appendDraw(format, pipeline.primitiveTopology)
            previousPipeline = pipeline
            draws.add(Draw(previousDraw!!, pipeline))
        }
        return vertexBuffer.getVertexBuilder(previousDraw!!)
    }

    private fun flush() {
        if (draws.isEmpty()) {
            camera = null
            return
        }
        vertexBuffer.upload()
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        RenderSystem.getProjectionType().applyLayeringTransform(modelView, 1f)

        val target = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        val colorView = target.colorTextureView ?: run {
            modelView.popMatrix()
            draws.clear()
            previousDraw = null
            previousPipeline = null
            camera = null
            return
        }
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "SkyCore WorldBoxes" },
            colorView,
            Optional.empty(),
            if (target.useDepth) target.depthTextureView else null,
            OptionalDouble.empty()
        ).use { pass ->
            RenderSystem.bindDefaultUniforms(pass)
            for (draw in draws) {
                drawPass(draw, pass)
            }
        }

        modelView.popMatrix()
        vertexBuffer.endDraw()
        vertexBuffer.endFrame()
        draws.clear()
        previousDraw = null
        previousPipeline = null
        camera = null
    }

    private fun drawPass(draw: Draw, pass: RenderPass) {
        val info = vertexBuffer.getExecuteInfo(draw.draw) ?: return
        pass.setPipeline(draw.pipeline)
        pass.setUniform(
            "DynamicTransforms",
            RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                Vector4f(1f, 1f, 1f, 1f)
            )
        )
        pass.setVertexBuffer(0, info.vertexBuffer().slice())
        pass.setIndexBuffer(info.indexBuffer(), info.indexType())
        pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0)
    }

    private fun quad(
        buffer: VertexConsumer,
        matrix: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)
        buffer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a)
        buffer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a)
    }

    private fun edge(
        buffer: VertexConsumer,
        matrix: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float, a: Float,
        width: Float
    ) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width)
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width)
    }

    private data class Draw(val draw: StagedVertexBuffer.Draw, val pipeline: RenderPipeline)
}
