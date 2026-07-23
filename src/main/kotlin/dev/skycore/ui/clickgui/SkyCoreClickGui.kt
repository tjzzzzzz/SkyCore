package dev.skycore.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import dev.skycore.SkyCore
import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.keybind.SkyCoreKeys
import dev.skycore.ui.render.Fonts
import dev.skycore.ui.hud.HudEditorScreen
import dev.skycore.ui.render.Icons
import dev.skycore.ui.render.Ui
import dev.skycore.ui.theme.Theme
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.roundToInt

class SkyCoreClickGui : Screen(Component.literal("SkyCore")) {

    private companion object {
        const val WINDOW_W = 528
        const val WINDOW_H = 344
        const val TITLE_H = 32
        const val SIDEBAR_W = 152
        const val ROW_H = 46
        const val CHILD_H = 26
        const val PAD = 12
        const val RADIUS = 5
        const val EDIT_BTN_H = 28
        const val SEARCH_W = 132

        val ICON: Identifier = Identifier.fromNamespaceAndPath(SkyCore.MOD_ID, "art/gui-icon.png")
        const val ICON_SRC = 32
        const val ICON_DRAW = 16

        val TITLE: Component = Fonts.label("SkyCore", Fonts.SEMIBOLD)
        val VERSION: Component = Fonts.label("v${SkyCore.version}", Fonts.SMALL)
        val SEARCH_HINT: Component = Fonts.label("Search", Fonts.SMALL)
        val EDIT_HUD: Component = Fonts.label("Edit HUD", Fonts.MEDIUM)
        val EDIT_BADGE: Component = Fonts.label("EDIT", Fonts.SMALL)
        val EMPTY_HINT: Component = Fonts.label("Nothing here yet", Fonts.REGULAR)
        val NO_RESULTS: Component = Fonts.label("No modules match that", Fonts.REGULAR)
        val LISTENING: Component = Fonts.label("PRESS A KEY", Fonts.SMALL)
    }

    private var windowX = 0
    private var windowY = 0

    private var dragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    private var selected = GuiCategory.GENERAL
    private var scroll = 0f

    private val categoryFade = FloatArray(GuiCategory.entries.size)
    private var closeFade = 0f
    private var editFade = 0f

    private var lastFrameNanos = 0L
    private var listening: KeybindOption? = null
    private var draggingSlider: SliderOption? = null
    private var sliderTrackX = 0
    private var sliderTrackW = 0

    private val badgeCache = HashMap<KeybindOption, Pair<String, Component>>()

    private var searchFocused = false
    private var searchQuery = ""
    private var caretBlink = 0f

    private var searchResults: List<SearchEngine.Hit> = emptyList()
    private var searchLabel: Component = Fonts.label("", Fonts.REGULAR)
    private val categoryBadges = HashMap<GuiCategory, Component>()

    private val searching: Boolean get() = searchQuery.isNotEmpty()

    private val searchExpanded = HashSet<GuiOption>()

    private fun refreshSearch() {
        searchResults = SearchEngine.search(searchQuery)
        searchLabel = Fonts.label(searchQuery, Fonts.REGULAR)
        scroll = 0f

        searchExpanded.clear()
        for (hit in searchResults) {
            if (hit.viaChild && hit.option.hasChildren) searchExpanded.add(hit.option)
        }
    }

    private inline fun forEachSearchRow(
        action: (option: GuiOption, hit: SearchEngine.Hit?, depth: Int, offsetY: Int) -> Unit
    ) {
        var y = 0
        for (hit in searchResults) {
            action(hit.option, hit, 0, y)
            y += ROW_H
            if (searchExpanded.contains(hit.option)) {
                for (child in hit.option.children) {
                    action(child, null, 1, y)
                    y += CHILD_H
                }
            }
        }
    }

    private fun searchContentHeight(): Int {
        var h = 0
        for (hit in searchResults) {
            h += ROW_H
            if (searchExpanded.contains(hit.option)) h += hit.option.children.size * CHILD_H
        }
        return h
    }

    private fun categoryBadge(category: GuiCategory): Component =
        categoryBadges.getOrPut(category) { Fonts.label(category.displayName.uppercase(), Fonts.SMALL) }

    override fun init() {
        if (windowX == 0 && windowY == 0) {
            windowX = (width - WINDOW_W) / 2
            windowY = (height - WINDOW_H) / 2
        }
        windowX = windowX.coerceIn(0, max(0, width - WINDOW_W))
        windowY = windowY.coerceIn(0, max(0, height - WINDOW_H))
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = now

        Ui.shadow(g, windowX, windowY, WINDOW_W, WINDOW_H)
        Ui.glow(g, windowX, windowY, WINDOW_W, WINDOW_H, Theme.ACCENT, 1)
        Ui.roundedRect(g, windowX, windowY, WINDOW_W, WINDOW_H, Theme.SURFACE, RADIUS)

        drawSidebarPlate(g)
        drawTitleBar(g, mouseX, mouseY, dt)
        drawSidebar(g, mouseX, mouseY, dt)
        drawOptions(g, mouseX, mouseY, dt)

        Ui.frame(g, windowX, windowY, WINDOW_W, WINDOW_H, Theme.BORDER, RADIUS)
    }

    private fun drawSidebarPlate(g: GuiGraphicsExtractor) {
        Ui.roundedRect(g, windowX, windowY, SIDEBAR_W, WINDOW_H, Theme.SURFACE_RAISED, RADIUS)

        g.fill(windowX + SIDEBAR_W - RADIUS, windowY, windowX + SIDEBAR_W, windowY + WINDOW_H, Theme.SURFACE_RAISED)
        g.fill(windowX + SIDEBAR_W, windowY, windowX + SIDEBAR_W + 1, windowY + WINDOW_H, Theme.BORDER)
    }

    private fun drawTitleBar(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        val baseline = windowY + 11

        g.blit(
            RenderPipelines.GUI_TEXTURED, ICON,
            windowX + PAD, windowY + (TITLE_H - ICON_DRAW) / 2,
            0f, 0f,
            ICON_DRAW, ICON_DRAW,
            ICON_SRC, ICON_SRC,
            ICON_SRC, ICON_SRC
        )
        g.text(font, TITLE, windowX + PAD + ICON_DRAW + 7, baseline, Theme.TEXT, false)

        val vw = Fonts.width(VERSION)
        val vx = windowX + PAD + ICON_DRAW + 7 + Fonts.width(TITLE) + 8
        Ui.panel(g, vx, windowY + 10, vw + 12, 14, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 4)
        g.text(font, VERSION, vx + 6, windowY + 14, Theme.TEXT_FAINT, false)

        val closeX = windowX + WINDOW_W - PAD - 11

        val searchW = SEARCH_W
        val searchX = closeX - 10 - searchW
        Ui.panel(
            g, searchX, windowY + 9, searchW, 16, Theme.CONTROL_OFF,
            if (searchFocused) Theme.ACCENT else Theme.BORDER_SOFT, 4
        )
        Icons.magnifier(g, searchX + 7, windowY + 13, if (searchFocused) Theme.ACCENT else Theme.TEXT_FAINT)

        if (searching) {
            g.text(font, searchLabel, searchX + 20, windowY + 14, Theme.TEXT, false)
            if (searchFocused) {
                caretBlink += dt
                if (caretBlink % 1.06f < 0.53f) {
                    val caretX = searchX + 20 + Fonts.width(searchLabel) + 1
                    g.fill(caretX, windowY + 13, caretX + 1, windowY + 21, Theme.ACCENT)
                }
            }
        } else {
            g.text(font, SEARCH_HINT, searchX + 20, windowY + 14, Theme.TEXT_FAINT, false)
        }

        val closeHover = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), closeX - 4, windowY + 8, 19, 17)
        closeFade = Ui.approach(closeFade, if (closeHover) 1f else 0f, dt)
        if (closeFade > 0.01f) {
            Ui.roundedRect(g, closeX - 5, windowY + 8, 21, 17, Ui.withAlpha(Theme.DANGER, 0.16f * closeFade), 4)
        }
        Ui.cross(g, closeX, windowY + 13, 7, Ui.lerpColor(Theme.TEXT_MUTED, Theme.DANGER, closeFade))

        g.fill(windowX + SIDEBAR_W + 1, windowY + TITLE_H, windowX + WINDOW_W, windowY + TITLE_H + 1, Theme.BORDER)
    }

    private fun drawSidebar(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        val top = windowY + TITLE_H + 10

        for ((index, category) in GuiCategory.entries.withIndex()) {
            val rowY = top + index * 34
            val rowX = windowX + 8
            val rowW = SIDEBAR_W - 16
            val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), rowX, rowY, rowW, 28)
            val active = category == selected

            val target = if (active) 1f else if (hovered) 0.5f else 0f
            categoryFade[index] = Ui.approach(categoryFade[index], target, dt)
            val fade = categoryFade[index]

            if (fade > 0.01f) {
                Ui.roundedRect(g, rowX, rowY, rowW, 28, Ui.withAlpha(Theme.HOVER, fade), 4)
            }

            if (active || fade > 0.6f) {
                val barH = (16 * fade).roundToInt()
                if (barH > 0) {
                    val barY = rowY + (28 - barH) / 2
                    Ui.roundedRect(g, rowX - 6, barY - 1, 4, barH + 2, Ui.withAlpha(Theme.ACCENT, 0.25f * fade), 2)
                    Ui.roundedRect(g, rowX - 5, barY, 2, barH, Theme.ACCENT, 1)
                }
            }

            val tint = Ui.lerpColor(Theme.TEXT_MUTED, Theme.ACCENT, fade)
            when (category) {
                GuiCategory.GENERAL -> Icons.sliders(g, rowX + 10, rowY + 9, tint)
                GuiCategory.VISUALS -> Icons.eye(g, rowX + 10, rowY + 9, tint)
                GuiCategory.MINING -> Icons.pickaxe(g, rowX + 10, rowY + 9, tint)
                GuiCategory.DUNGEONS -> Icons.castle(g, rowX + 10, rowY + 9, tint)
                GuiCategory.ECONOMY -> Icons.coin(g, rowX + 10, rowY + 9, tint)
                GuiCategory.WAYPOINTS -> Icons.pin(g, rowX + 10, rowY + 9, tint)
            }

            val textColor = Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, fade)
            g.text(font, category.label, rowX + 28, rowY + 10, textColor, false)
        }

        drawEditButton(g, mouseX, mouseY, dt)
    }

    private fun drawEditButton(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        val x = windowX + 8
        val y = windowY + WINDOW_H - EDIT_BTN_H - 10
        val w = SIDEBAR_W - 16

        val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, y, w, EDIT_BTN_H)
        editFade = Ui.approach(editFade, if (hovered) 1f else 0f, dt)

        Ui.panel(
            g, x, y, w, EDIT_BTN_H,
            Ui.lerpColor(Theme.CONTROL_OFF, Theme.HOVER, editFade),
            Ui.lerpColor(Theme.BORDER_SOFT, Theme.ACCENT, editFade),
            5
        )
        val tint = Ui.lerpColor(Theme.TEXT_MUTED, Theme.ACCENT, editFade)
        Icons.layout(g, x + 12, y + 9, tint)
        g.text(font, EDIT_HUD, x + 30, y + 10, Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, editFade), false)
    }

    private fun drawOptions(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        val paneX = windowX + SIDEBAR_W + 1
        val paneY = windowY + TITLE_H + 1
        val paneW = WINDOW_W - SIDEBAR_W - 1
        val paneH = WINDOW_H - TITLE_H - 1

        if (searching) {
            drawSearchResults(g, paneX, paneY, paneW, paneH, mouseX, mouseY, dt)
            return
        }

        val options = ClickGuiRegistry.optionsFor(selected)
        if (options.isEmpty()) {
            g.text(font, EMPTY_HINT, paneX + PAD + 4, paneY + PAD + 4, Theme.TEXT_FAINT, false)
            return
        }

        val contentH = contentHeight(options) + PAD * 2
        val maxScroll = max(0, contentH - paneH).toFloat()
        scroll = scroll.coerceIn(0f, maxScroll)

        g.enableScissor(paneX, paneY, paneX + paneW, paneY + paneH)

        val rowX = paneX + PAD
        val rowW = paneW - PAD * 2

        forEachVisibleRow(options) { option, depth, offsetY ->
            val rowY = paneY + PAD + offsetY - scroll.roundToInt()
            val rowH = if (depth == 0) ROW_H - 8 else CHILD_H - 4

            if (rowY + rowH < paneY || rowY > paneY + paneH) {
                option.hover = Ui.approach(option.hover, 0f, dt)
                if (option is ToggleOption) {
                    option.knob = Ui.approach(option.knob, if (option.enabled) 1f else 0f, dt)
                }
                return@forEachVisibleRow
            }

            val indent = depth * 20
            val x = rowX + indent
            val w = rowW - indent
            val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), x, rowY, w, rowH)

            option.hover = Ui.approach(option.hover, if (hovered) 1f else 0f, dt)
            if (option is ToggleOption) {
                option.knob = Ui.approach(option.knob, if (option.enabled) 1f else 0f, dt)
            }

            if (option.hover > 0.01f) {
                Ui.roundedRect(g, x, rowY, w, rowH, Ui.withAlpha(Theme.HOVER, option.hover), 4)
                Ui.frame(g, x, rowY, w, rowH, Ui.withAlpha(Theme.BORDER_SOFT, option.hover * 0.8f), 4)
            }

            val on = option is ToggleOption && option.knob > 0.5f
            val titleColor =
                if (on) Theme.TEXT
                else Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, 0.45f + option.hover * 0.55f)

            if (depth == 0) {
                var textX = x + 12
                if (option.hasChildren) {
                    Icons.chevron(
                        g, x + 10, rowY + 10,
                        Ui.lerpColor(Theme.TEXT_FAINT, Theme.ACCENT, option.hover),
                        option.expanded
                    )
                    textX = x + 24
                }
                g.text(font, option.titleLabel, textX, rowY + 8, titleColor, false)
                g.text(font, option.descLabel, textX, rowY + 21, Theme.TEXT_MUTED, false)
            } else {

                g.fill(x - 10, rowY + 4, x - 9, rowY + rowH - 4, Theme.BORDER_SOFT)
                g.text(font, option.titleLabel, x + 12, rowY + (rowH - 8) / 2, titleColor, false)
            }

            when (option) {

                is ToggleOption -> {
                    val toggleX = if (depth == 0) x + w - 30 else x + w - 26
                    val toggleSize = if (depth == 0) 18 else 14
                    if (depth == 0 && option.editAction != null) {
                        drawEditBadge(g, toggleX - 8, rowY + (rowH - 17) / 2)
                    }
                    drawToggle(g, toggleX, rowY + (rowH - toggleSize) / 2, option, toggleSize)
                }
                is KeybindOption -> drawKeybind(g, x + w - 12, rowY + (rowH - 17) / 2, option)
                is SliderOption -> drawSlider(g, x + w - 12, rowY + (rowH - 10) / 2, option, depth)
            }
        }

        g.disableScissor()

        if (maxScroll > 0f) drawScrollbar(g, paneX + paneW - 4, paneY + 4, paneH - 8, contentH, maxScroll)
    }

    private fun drawSearchResults(
        g: GuiGraphicsExtractor,
        paneX: Int, paneY: Int, paneW: Int, paneH: Int,
        mouseX: Int, mouseY: Int, dt: Float
    ) {
        if (searchResults.isEmpty()) {
            g.text(font, NO_RESULTS, paneX + PAD + 4, paneY + PAD + 4, Theme.TEXT_FAINT, false)
            return
        }

        val contentH = searchContentHeight() + PAD * 2
        val maxScroll = max(0, contentH - paneH).toFloat()
        scroll = scroll.coerceIn(0f, maxScroll)

        g.enableScissor(paneX, paneY, paneX + paneW, paneY + paneH)

        val baseX = paneX + PAD
        val baseW = paneW - PAD * 2

        forEachSearchRow { option, hit, depth, offsetY ->
            val rowY = paneY + PAD + offsetY - scroll.roundToInt()
            val rowH = if (depth == 0) ROW_H - 8 else CHILD_H - 4

            if (rowY + rowH < paneY || rowY > paneY + paneH) {
                option.hover = Ui.approach(option.hover, 0f, dt)
                if (option is ToggleOption) {
                    option.knob = Ui.approach(option.knob, if (option.enabled) 1f else 0f, dt)
                }
                return@forEachSearchRow
            }

            val indent = depth * 20
            val rowX = baseX + indent
            val rowW = baseW - indent
            val hovered = Ui.inBounds(mouseX.toDouble(), mouseY.toDouble(), rowX, rowY, rowW, rowH)

            option.hover = Ui.approach(option.hover, if (hovered) 1f else 0f, dt)
            if (option is ToggleOption) {
                option.knob = Ui.approach(option.knob, if (option.enabled) 1f else 0f, dt)
            }

            if (option.hover > 0.01f) {
                Ui.roundedRect(g, rowX, rowY, rowW, rowH, Ui.withAlpha(Theme.HOVER, option.hover), 4)
                Ui.frame(g, rowX, rowY, rowW, rowH, Ui.withAlpha(Theme.BORDER_SOFT, option.hover * 0.8f), 4)
            }

            val on = option is ToggleOption && option.knob > 0.5f
            val titleColor =
                if (on) Theme.TEXT
                else Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, 0.45f + option.hover * 0.55f)

            if (depth == 0) {
                var textX = rowX + 12
                if (option.hasChildren) {
                    Icons.chevron(
                        g, rowX + 10, rowY + 10,
                        Ui.lerpColor(Theme.TEXT_FAINT, Theme.ACCENT, option.hover),
                        searchExpanded.contains(option)
                    )
                    textX = rowX + 24
                }
                g.text(font, option.titleLabel, textX, rowY + 8, titleColor, false)
                g.text(font, option.descLabel, textX, rowY + 21, Theme.TEXT_MUTED, false)

                if (hit != null) {
                    val badge = categoryBadge(hit.category)
                    val badgeW = Fonts.width(badge)
                    val badgeX = rowX + rowW - badgeW - 52
                    Ui.panel(g, badgeX - 6, rowY + 8, badgeW + 12, 13, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 3)
                    g.text(font, badge, badgeX, rowY + 11, Theme.TEXT_FAINT, false)
                }
            } else {
                g.fill(rowX - 10, rowY + 4, rowX - 9, rowY + rowH - 4, Theme.BORDER_SOFT)
                g.text(font, option.titleLabel, rowX + 12, rowY + (rowH - 8) / 2, titleColor, false)
            }

            when (option) {
                is ToggleOption -> {
                    val toggleX = if (depth == 0) rowX + rowW - 30 else rowX + rowW - 26
                    val toggleSize = if (depth == 0) 18 else 14
                    if (depth == 0 && option.editAction != null) {
                        drawEditBadge(g, toggleX - 8, rowY + (rowH - 17) / 2)
                    }
                    drawToggle(g, toggleX, rowY + (rowH - toggleSize) / 2, option, toggleSize)
                }
                is KeybindOption -> drawKeybind(g, rowX + rowW - 12, rowY + (rowH - 17) / 2, option)
                is SliderOption -> drawSlider(g, rowX + rowW - 12, rowY + (rowH - 10) / 2, option, depth)
            }
        }

        g.disableScissor()

        if (maxScroll > 0f) drawScrollbar(g, paneX + paneW - 4, paneY + 4, paneH - 8, contentH, maxScroll)
    }

    private inline fun forEachVisibleRow(
        options: List<GuiOption>,
        action: (option: GuiOption, depth: Int, offsetY: Int) -> Unit
    ) {
        var y = 0
        for (parent in options) {
            action(parent, 0, y)
            y += ROW_H
            if (parent.expanded) {
                for (child in parent.children) {
                    action(child, 1, y)
                    y += CHILD_H
                }
            }
        }
    }

    private fun contentHeight(options: List<GuiOption>): Int {
        var h = 0
        for (parent in options) {
            h += ROW_H
            if (parent.expanded) h += parent.children.size * CHILD_H
        }
        return h
    }

    private fun drawToggle(g: GuiGraphicsExtractor, x: Int, y: Int, option: ToggleOption, size: Int = 18) {
        val t = option.knob

        if (t > 0.01f) {
            Ui.roundedRect(g, x - 1, y - 1, size + 2, size + 2, Ui.withAlpha(Theme.ACCENT, 0.16f * t), 6)
        }

        Ui.panel(
            g, x, y, size, size,
            Ui.lerpColor(Theme.CONTROL_OFF, Theme.CONTROL_ON, t),
            Ui.lerpColor(
                Ui.lerpColor(Theme.BORDER_SOFT, Theme.TEXT_FAINT, option.hover * 0.6f),
                Theme.ACCENT,
                t
            ),
            5
        )

        if (t > 0.02f) {
            Ui.check(g, x + (size - 8) / 2, y + (size - 6) / 2, Ui.withAlpha(Theme.ACCENT, t))
        }
    }

    private fun drawKeybind(g: GuiGraphicsExtractor, rightX: Int, y: Int, option: KeybindOption) {
        val active = listening === option
        val label = if (active) LISTENING else badgeFor(option)

        val textW = Fonts.width(label)
        val boxW = max(46, textW + 18)
        val x = rightX - boxW

        if (active) {
            Ui.roundedRect(g, x - 1, y - 1, boxW + 2, 19, Ui.withAlpha(Theme.ACCENT, 0.18f), 5)
        }
        Ui.panel(
            g, x, y, boxW, 17,
            Theme.CONTROL_OFF,
            if (active) Theme.ACCENT
            else Ui.lerpColor(Theme.BORDER_SOFT, Theme.TEXT_FAINT, option.hover * 0.7f),
            4
        )
        g.text(
            font, label, x + (boxW - textW) / 2, y + 5,
            if (active) Theme.ACCENT else Ui.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, option.hover),
            false
        )
    }

    private fun drawEditBadge(g: GuiGraphicsExtractor, rightX: Int, y: Int) {
        val textW = Fonts.width(EDIT_BADGE)
        val boxW = textW + 14
        val x = rightX - boxW
        Ui.panel(g, x, y, boxW, 17, Theme.CONTROL_OFF, Theme.BORDER_SOFT, 4)
        g.text(font, EDIT_BADGE, x + 7, y + 5, Theme.ACCENT, false)
    }

    private fun editBadgeWidth(): Int = Fonts.width(EDIT_BADGE) + 14

    private fun editBadgeHit(
        option: ToggleOption,
        rowX: Int,
        rowW: Int,
        rowY: Int,
        rowH: Int,
        mx: Double,
        my: Double
    ): Boolean {
        if (option.editAction == null) return false
        val toggleX = rowX + rowW - 30
        val boxW = editBadgeWidth()
        val x = toggleX - 8 - boxW
        val y = rowY + (rowH - 17) / 2
        return Ui.inBounds(mx, my, x, y, boxW, 17)
    }

    private fun drawSlider(g: GuiGraphicsExtractor, rightX: Int, y: Int, option: SliderOption, depth: Int) {
        val trackW = if (depth == 0) 110 else 96
        val value = option.valueLabel
        val valueW = Fonts.width(value)
        val trackX = rightX - valueW - 8 - trackW

        Ui.roundedRect(g, trackX, y + 3, trackW, 4, Theme.CONTROL_OFF, 2)
        val fill = (trackW * option.progress()).roundToInt().coerceAtLeast(0)
        if (fill > 0) {
            Ui.roundedRect(g, trackX, y + 3, fill, 4, Theme.ACCENT, 2)
        }
        val knobX = trackX + fill - 3
        Ui.roundedRect(g, knobX, y, 7, 10, Theme.TEXT, 3)
        if (draggingSlider === option) {
            Ui.roundedRect(g, knobX - 1, y - 1, 9, 12, Ui.withAlpha(Theme.ACCENT, 0.25f), 4)
        }
        g.text(font, value, rightX - valueW, y + 1, Theme.TEXT_MUTED, false)
    }

    private fun badgeFor(option: KeybindOption): Component {
        val current = option.boundKeyLabel
        val cached = badgeCache[option]
        if (cached != null && cached.first == current) return cached.second

        val built = Fonts.label(current.uppercase(), Fonts.SMALL)
        badgeCache[option] = current to built
        return built
    }

    private fun drawScrollbar(g: GuiGraphicsExtractor, x: Int, y: Int, h: Int, contentH: Int, maxScroll: Float) {
        val thumbH = max(24, h * h / contentH)
        val progress = if (maxScroll <= 0f) 0f else scroll / maxScroll
        val thumbY = y + ((h - thumbH) * progress).roundToInt()
        Ui.roundedRect(g, x, y, 2, h, Ui.withAlpha(Theme.BORDER_SOFT, 0.5f), 1)
        Ui.roundedRect(g, x, thumbY, 2, thumbH, Ui.withAlpha(Theme.ACCENT, 0.7f), 1)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x
        val my = event.y

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            val paneX = windowX + SIDEBAR_W + 1
            val paneY = windowY + TITLE_H + 1
            val paneW = WINDOW_W - SIDEBAR_W - 1
            val paneH = WINDOW_H - TITLE_H - 1

            if (Ui.inBounds(mx, my, paneX, paneY, paneW, paneH)) {
                var handled = false
                if (searching) {
                    forEachSearchRow { option, _, depth, offsetY ->
                        if (handled || depth != 0 || !option.hasChildren) return@forEachSearchRow
                        val rowY = paneY + PAD + offsetY - scroll.roundToInt()
                        if (Ui.inBounds(mx, my, paneX + PAD, rowY, paneW - PAD * 2, ROW_H - 8)) {
                            if (!searchExpanded.remove(option)) searchExpanded.add(option)
                            handled = true
                        }
                    }
                } else {
                    forEachVisibleRow(ClickGuiRegistry.optionsFor(selected)) { option, depth, offsetY ->
                        if (handled || depth != 0 || !option.hasChildren) return@forEachVisibleRow
                        val rowY = paneY + PAD + offsetY - scroll.roundToInt()
                        if (Ui.inBounds(mx, my, paneX + PAD, rowY, paneW - PAD * 2, ROW_H - 8)) {
                            option.expanded = !option.expanded
                            handled = true
                        }
                    }
                }
                if (handled) return true
            }
        }

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            val closeXs = windowX + WINDOW_W - PAD - 11
            val searchX = closeXs - 10 - SEARCH_W
            searchFocused = Ui.inBounds(mx, my, searchX, windowY + 9, SEARCH_W, 16)
            if (searchFocused) {
                caretBlink = 0f
                return true
            }
            val closeX = windowX + WINDOW_W - PAD - 11
            if (Ui.inBounds(mx, my, closeX - 5, windowY + 8, 21, 17)) {
                onClose()
                return true
            }

            if (Ui.inBounds(mx, my, windowX, windowY, WINDOW_W, TITLE_H)) {
                dragging = true
                dragOffsetX = (mx - windowX).roundToInt()
                dragOffsetY = (my - windowY).roundToInt()
                return true
            }

            val editY = windowY + WINDOW_H - EDIT_BTN_H - 10
            if (Ui.inBounds(mx, my, windowX + 8, editY, SIDEBAR_W - 16, EDIT_BTN_H)) {
                minecraft.setScreenAndShow(HudEditorScreen(this))
                return true
            }

            val top = windowY + TITLE_H + 10
            for ((index, category) in GuiCategory.entries.withIndex()) {
                val rowY = top + index * 34
                if (Ui.inBounds(mx, my, windowX + 8, rowY, SIDEBAR_W - 16, 28)) {
                    if (selected != category) {
                        selected = category
                        scroll = 0f
                        listening = null
                    }
                    return true
                }
            }

            val paneX = windowX + SIDEBAR_W + 1
            val paneY = windowY + TITLE_H + 1
            val paneW = WINDOW_W - SIDEBAR_W - 1
            val paneH = WINDOW_H - TITLE_H - 1

            if (searching && Ui.inBounds(mx, my, paneX, paneY, paneW, paneH)) {
                var handled = false
                forEachSearchRow { option, _, depth, offsetY ->
                    if (handled) return@forEachSearchRow
                    val rowY = paneY + PAD + offsetY - scroll.roundToInt()
                    val rowH = if (depth == 0) ROW_H - 8 else CHILD_H - 4
                    val indent = depth * 20
                    if (!Ui.inBounds(mx, my, paneX + PAD + indent, rowY, paneW - PAD * 2 - indent, rowH)) {
                        return@forEachSearchRow
                    }
                    handled = true

                    if (depth == 0 && option.hasChildren && mx < paneX + PAD + 22) {
                        if (!searchExpanded.remove(option)) searchExpanded.add(option)
                        return@forEachSearchRow
                    }

                    when (option) {
                        is ToggleOption -> {
                            val indent = depth * 20
                            val rowX = paneX + PAD + indent
                            val rowW = paneW - PAD * 2 - indent
                            if (depth == 0 && editBadgeHit(option, rowX, rowW, rowY, rowH, mx, my)) {
                                option.editAction?.invoke(this)
                            } else {
                                option.toggle()
                            }
                        }
                        is KeybindOption -> listening = if (listening === option) null else option
                        is SliderOption -> beginSliderDrag(option, mx, depth)
                    }
                }
                return true
            }

            if (Ui.inBounds(mx, my, paneX, paneY, paneW, paneH)) {
                var handled = false
                forEachVisibleRow(ClickGuiRegistry.optionsFor(selected)) { option, depth, offsetY ->
                    if (handled) return@forEachVisibleRow
                    val rowY = paneY + PAD + offsetY - scroll.roundToInt()
                    val rowH = if (depth == 0) ROW_H - 8 else CHILD_H - 4
                    val indent = depth * 20
                    if (!Ui.inBounds(mx, my, paneX + PAD + indent, rowY, paneW - PAD * 2 - indent, rowH)) {
                        return@forEachVisibleRow
                    }
                    handled = true

                    if (depth == 0 && option.hasChildren && mx < paneX + PAD + 22) {
                        option.expanded = !option.expanded
                        return@forEachVisibleRow
                    }

                    when (option) {
                        is ToggleOption -> {
                            val rowX = paneX + PAD + indent
                            val rowW = paneW - PAD * 2 - indent
                            if (depth == 0 && editBadgeHit(option, rowX, rowW, rowY, rowH, mx, my)) {
                                option.editAction?.invoke(this)
                            } else {
                                option.toggle()
                            }
                        }
                        is KeybindOption -> listening = if (listening === option) null else option
                        is SliderOption -> beginSliderDrag(option, mx, depth)
                    }
                }
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun beginSliderDrag(option: SliderOption, mouseX: Double, depth: Int) {
        val paneX = windowX + SIDEBAR_W + 1
        val paneW = WINDOW_W - SIDEBAR_W - 1
        val indent = depth * 20
        val rowRight = paneX + PAD + indent + (paneW - PAD * 2 - indent)
        val trackW = if (depth == 0) 110 else 96
        val valueW = Fonts.width(option.valueLabel)
        sliderTrackX = rowRight - 12 - valueW - 8 - trackW
        sliderTrackW = trackW
        draggingSlider = option
        applySliderDrag(mouseX)
    }

    private fun applySliderDrag(mouseX: Double) {
        val option = draggingSlider ?: return
        if (sliderTrackW <= 0) return
        option.setFromProgress(((mouseX - sliderTrackX) / sliderTrackW).toFloat())
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (draggingSlider != null) {
            applySliderDrag(event.x)
            return true
        }
        if (dragging) {
            windowX = (event.x - dragOffsetX).roundToInt().coerceIn(0, max(0, width - WINDOW_W))
            windowY = (event.y - dragOffsetY).roundToInt().coerceIn(0, max(0, height - WINDOW_H))
            return true
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        if (draggingSlider != null) {
            draggingSlider = null
            SkyCoreConfig.save()
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        scroll -= (vertical * 20).toFloat()
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!searchFocused || listening != null) return super.charTyped(event)
        if (!event.isAllowedChatCharacter) return false

        searchQuery += event.codepointAsString()
        caretBlink = 0f
        refreshSearch()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (searchFocused && listening == null) {
            when (event.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) {

                        searchQuery = searchQuery.substring(0, searchQuery.offsetByCodePoints(searchQuery.length, -1))
                        caretBlink = 0f
                        refreshSearch()
                    }
                    return true
                }

                GLFW.GLFW_KEY_ESCAPE -> {
                    if (searching) {
                        searchQuery = ""
                        refreshSearch()
                    } else {
                        searchFocused = false
                    }
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {

                    val top = searchResults.firstOrNull()?.option
                    if (top is ToggleOption) top.toggle()
                    return true
                }
            }
        }

        val pending = listening
        if (pending != null) {

            listening = null
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> Unit
                GLFW.GLFW_KEY_DELETE -> SkyCoreKeys.rebind(pending.mapping, InputConstants.UNKNOWN)
                else -> SkyCoreKeys.rebind(pending.mapping, InputConstants.getKey(event))
            }
            return true
        }

        if (!searchFocused) {
            val bound = KeyMappingHelper.getBoundKeyOf(SkyCoreKeys.openClickGui)
            if (bound != InputConstants.UNKNOWN && InputConstants.getKey(event) == bound) {
                onClose()
                return true
            }
        }
        return super.keyPressed(event)
    }
}
