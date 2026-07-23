package dev.skycore.ui.hud

import dev.skycore.SkyCore
import dev.skycore.core.stats.ServerStats
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

object HudManager {

    val widgets: List<HudWidget> by lazy {
        listOf(InfoBarWidget(), SprintWidget(), CommissionsWidget(), SkillTrackerWidget())
    }

    @Volatile
    var editing: Boolean = false

    fun init() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(SkyCore.MOD_ID, "hud"),
            { g, _ ->

                if (!editing) renderAll(g, false)
            }
        )

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ServerStats.reset() }

        ClientTickEvents.END_CLIENT_TICK.register { ServerStats.tickPing() }
    }

    fun renderAll(g: net.minecraft.client.gui.GuiGraphicsExtractor, forceVisible: Boolean) {
        val sw = g.guiWidth()
        val sh = g.guiHeight()

        for (widget in widgets) {
            if (!forceVisible && !widget.enabled) continue

            val pose = g.pose()
            pose.pushMatrix()
            pose.translate(widget.xFor(sw).toFloat(), widget.yFor(sh).toFloat())
            pose.scale(widget.scale, widget.scale)
            widget.render(g, forceVisible)
            pose.popMatrix()
        }
    }
}
