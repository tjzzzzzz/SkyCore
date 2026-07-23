package dev.skycore.core.skyblock

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

object ScoreboardCache {

    fun lines(): List<String> {
        val client = Minecraft.getInstance()
        val scoreboard = client.level?.scoreboard ?: return emptyList()
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        val result = ArrayList<String>()
        for (holder in scoreboard.trackedPlayers) {
            if (!scoreboard.listPlayerScores(holder).containsKey(objective)) continue
            val team = scoreboard.getPlayersTeam(holder.scoreboardName) ?: continue
            val line = ChatFormatting.stripFormatting(
                team.playerPrefix.string + team.playerSuffix.string
            )?.trim().orEmpty()
            if (line.isNotEmpty()) result.add(line)
        }
        return result
    }
}
