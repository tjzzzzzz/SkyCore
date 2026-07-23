package dev.skycore.core.location

enum class IslandType(val apiId: String, val displayName: String) {
    HUB("hub", "Hub"),
    GARDEN("garden", "Garden"),
    DUNGEONS("dungeon", "Dungeons"),
    SLAYER("", "Slayer"),
    PRIVATE_ISLAND("dynamic", "Private Island"),
    DWARVEN_MINES("mining_3", "Dwarven Mines"),
    CRYSTAL_HOLLOWS("crystal_hollows", "Crystal Hollows"),
    GLACITE_MINESHAFTS("mineshaft", "Glacite Mineshafts"),
    THE_END("combat_3", "The End"),
    CRIMSON_ISLE("crimson_isle", "Crimson Isle"),
    UNKNOWN("unknown", "Unknown");

    val isMiningIsland: Boolean
        get() = this == DWARVEN_MINES || this == CRYSTAL_HOLLOWS || this == GLACITE_MINESHAFTS

    companion object {
        private val byApiId = entries
            .filter { it.apiId.isNotEmpty() }
            .associateBy { it.apiId }

        fun fromApiId(id: String?): IslandType =
            if (id.isNullOrEmpty()) UNKNOWN else byApiId[id] ?: UNKNOWN

        fun fromSidebarName(line: String): IslandType {
            val name = line.lowercase()
            return when {
                name.contains("your island") -> PRIVATE_ISLAND
                name.contains("garden") -> GARDEN
                name.contains("catacombs") || name.contains("dungeon") -> DUNGEONS
                name.contains("dwarven") || name.contains("glacite tunnels") || name.contains("glacite mineshaft") -> DWARVEN_MINES
                name.contains("crystal hollow") -> CRYSTAL_HOLLOWS
                name.contains("mineshaft") -> GLACITE_MINESHAFTS
                name.contains("the end") || name.contains("dragon's nest") -> THE_END
                name.contains("crimson") || name.contains("stronghold") || name.contains("bastion") -> CRIMSON_ISLE
                name.contains("hub") || name.contains("village") -> HUB
                else -> UNKNOWN
            }
        }
    }
}
