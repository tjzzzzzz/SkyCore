package dev.skylite.ui.theme

/**
 * single source of truth for the cyberpunk slate palette. every widget, hud
 * element and config screen pulls its colours from here so a brand tweak is a
 * one file change.
 *
 * values are 0xAARRGGBB, ready to hand straight to the gui extractor.
 */
object Theme {

    /** neon cyan, the only accent we use */
    const val ACCENT = 0xFF00E5FF.toInt()

    /** dimmed accent for glows and inactive indicators */
    const val ACCENT_SOFT = 0x5900E5FF

    /** window card, charcoal at 92% */
    const val SURFACE = 0xEB0F1317.toInt()

    /** same charcoal fully opaque, for insets that sit on top of the card */
    const val SURFACE_SOLID = 0xFF0F1317.toInt()

    /** raised surface, used by the sidebar rail and row cards */
    const val SURFACE_RAISED = 0xFF141A20.toInt()

    /** hover wash behind sidebar entries and rows */
    const val HOVER = 0xFF1A2128.toInt()

    /** razor sharp 1px outer border */
    const val BORDER = 0xFF1F2832.toInt()

    /** inner borders on controls, a touch lighter so they read as inset */
    const val BORDER_SOFT = 0xFF2A3542.toInt()

    /** toggle body when off */
    const val CONTROL_OFF = 0xFF161C22.toInt()

    /**
     * toggle body when on. a dark cyan wash rather than a solid accent fill,
     * a saturated block at this size reads like a battery icon.
     */
    const val CONTROL_ON = 0xFF102A31.toInt()

    /** soft white, primary copy */
    const val TEXT = 0xFFF0F4F8.toInt()

    /** cool gray, descriptions and secondary copy */
    const val TEXT_MUTED = 0xFF7C8C9E.toInt()

    /** dimmer still, for placeholder and hint text */
    const val TEXT_FAINT = 0xFF55636F.toInt()

    /** status colours, kept in the same cool family so nothing clashes */
    const val SUCCESS = 0xFF3DDC97.toInt()
    const val WARNING = 0xFFFFC857.toInt()
    const val DANGER = 0xFFFF5C7A.toInt()

    /** ambient drop shadow under the window */
    const val SHADOW = 0xFF000000.toInt()

    /** legacy formatting code for chat output, closest match to the accent */
    const val CHAT_ACCENT = "§b"
    const val CHAT_MUTED = "§7"
}
