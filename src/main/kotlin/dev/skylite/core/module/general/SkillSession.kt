package dev.skylite.core.module.general

class SkillSession {
    var countedTicks: Long = 0
    var timestamp: Long = System.currentTimeMillis()
    var pauseTicks: Long = 0
    var lastPart: String = ""
    var lastExp: Double = 0.0
    var currentExp: Double = 0.0
    var totalTicks: Long = 0

    fun reset() {
        countedTicks = 0
        timestamp = System.currentTimeMillis()
        pauseTicks = 0
        lastPart = ""
        lastExp = 0.0
        currentExp = 0.0
        totalTicks = 0
    }
}
