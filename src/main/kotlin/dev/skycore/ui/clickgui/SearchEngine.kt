package dev.skycore.ui.clickgui

import java.util.Locale

object SearchEngine {

    class Hit(
        val option: GuiOption,
        val category: GuiCategory,
        val score: Int,
        val viaChild: Boolean
    )

    private const val NAME_EXACT = 1000
    private const val NAME_PREFIX = 700
    private const val NAME_WORD = 550
    private const val NAME_CONTAINS = 420
    private const val KEYWORD = 360
    private const val CATEGORY = 200
    private const val DESC_WORD = 180
    private const val DESC_CONTAINS = 120
    private const val FUZZY_NAME = 90
    private const val FUZZY_DESC = 30

    private const val CHILD_PENALTY = 40

    fun search(query: String): List<Hit> {
        val tokens = query.lowercase(Locale.ENGLISH).trim().split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        val hits = ArrayList<Hit>()
        for (category in GuiCategory.entries) {
            for (option in ClickGuiRegistry.optionsFor(category)) {
                var total = 0
                var viaChild = false
                var matched = true

                for (token in tokens) {
                    val direct = scoreToken(option, category, token)

                    var best = direct
                    var fromChild = false
                    for (child in option.children) {
                        val childScore = scoreToken(child, category, token) - CHILD_PENALTY
                        if (childScore > best) {
                            best = childScore
                            fromChild = true
                        }
                    }

                    if (best <= 0) {
                        matched = false
                        break
                    }
                    total += best
                    if (fromChild) viaChild = true
                }

                if (matched) hits.add(Hit(option, category, total, viaChild))
            }
        }

        hits.sortWith(compareByDescending<Hit> { it.score }.thenBy { it.option.title })
        return hits
    }

    private fun scoreToken(option: GuiOption, category: GuiCategory, token: String): Int {
        val name = option.title.lowercase(Locale.ENGLISH)
        val desc = option.description.lowercase(Locale.ENGLISH)
        val cat = category.displayName.lowercase(Locale.ENGLISH)

        if (name == token) return NAME_EXACT
        if (name.startsWith(token)) return NAME_PREFIX
        if (containsWordStart(name, token)) return NAME_WORD
        if (name.contains(token)) return NAME_CONTAINS

        for (keyword in option.keywords) {
            val k = keyword.lowercase(Locale.ENGLISH)
            if (k == token || k.startsWith(token)) return KEYWORD
            if (k.contains(token)) return KEYWORD - 60
        }

        if (cat.startsWith(token)) return CATEGORY
        if (containsWordStart(desc, token)) return DESC_WORD
        if (desc.contains(token)) return DESC_CONTAINS

        if (isSubsequence(name, token)) return FUZZY_NAME
        if (isSubsequence(desc, token)) return FUZZY_DESC

        return 0
    }

    private fun containsWordStart(haystack: String, token: String): Boolean {
        var index = haystack.indexOf(token)
        while (index >= 0) {
            if (index == 0 || !haystack[index - 1].isLetterOrDigit()) return true
            index = haystack.indexOf(token, index + 1)
        }
        return false
    }

    private fun isSubsequence(haystack: String, token: String): Boolean {
        if (token.length < 2) return false
        var t = 0
        for (c in haystack) {
            if (c == token[t]) {
                t++
                if (t == token.length) return true
            }
        }
        return false
    }
}
