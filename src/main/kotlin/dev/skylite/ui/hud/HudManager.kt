package dev.skylite.ui.hud

import dev.skylite.SkyLite
import dev.skylite.core.stats.ServerStats
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

/**
 * owns every hud widget: draws them in game, hands them to the editor, and
 * keeps their placement in the config.
 */
object HudManager {

    /**
     * built on first use rather than at class init. widgets measure text, and
     * nothing may touch the font before the client has finished starting.
     */
    val widgets: List<HudWidget> by lazy {
        listOf(InfoBarWidget(), SprintWidget(), CommissionsWidget(), SkillTrackerWidget())
    }

    /** set by the editor so widgets keep drawing while it is open */
    @Volatile
    var editing: Boolean = false

    fun init() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(SkyLite.MOD_ID, "hud"),
            { g, _ ->
                // the editor draws the widgets itself so it can add handles
                if (!editing) renderAll(g, false)
            }
        )

        // a fresh server means the old tick rate reading is meaningless
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ServerStats.reset() }

        // drives our own ping request, the tab list latency is not trustworthy
        ClientTickEvents.END_CLIENT_TICK.register { ServerStats.tickPing() }
    }

    /**
     * 26.2 dropped Options.hideGui, f1 is handled by the layered hud itself so
     * an element attached through the registry is already skipped when hidden.
     */
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
