package com.example

import java.util.UUID

enum class Charge {
    POSITIVE, NEGATIVE
}

enum class MemoryType {
    VISITED, DANGER, RESOURCE
}

data class BotMemory(
    val x: Int,
    val y: Int,
    val type: MemoryType,
    val intensity: Double = 1.0,
    val age: Int = 0
)

data class Bot(
    val id: String,
    var x: Int,
    var y: Int,
    var charge: Charge,
    var intelligence: Int = 1,
    var energy: Double = 40.0,
    var xp: Double = 0.0,
    var levelUpThreshold: Double = 50.0,
    var gracePeriod: Int = 0,
    var age: Int = 0,
    var memories: List<BotMemory> = emptyList(),
    var totalConsumed: Double = 0.0
) {
    val isRevealed: Boolean
        get() = gracePeriod <= 0

    fun getMetabolicRate(baseSurvivalCost: Double): Double {
        val cloakCost = if (!isRevealed) 0.5 else 0.0
        return baseSurvivalCost + (intelligence * 0.4) + cloakCost
    }

    fun updateXp(amount: Double) {
        xp += amount
        if (xp >= levelUpThreshold) {
            intelligence += 1
            xp = 0.0
            levelUpThreshold *= 1.4
        }
    }

    fun addMemory(x: Int, y: Int, type: MemoryType, maxCapacity: Int) {
        val updated = memories.toMutableList()
        val index = updated.indexOfFirst { it.x == x && it.y == y && it.type == type }
        if (index >= 0) {
            // Refresh
            updated[index] = BotMemory(x, y, type, 1.0, 0)
        } else {
            if (updated.size >= maxCapacity && updated.isNotEmpty()) {
                // Remove oldest
                updated.sortByDescending { it.age }
                updated.removeAt(0)
            }
            updated.add(BotMemory(x, y, type, 1.0, 0))
        }
        memories = updated
    }

    fun decayMemories(maxAge: Int) {
        memories = memories.map {
            val newAge = it.age + 1
            val decayRatio = 1.0 - (newAge.toDouble() / maxAge.toDouble())
            it.copy(age = newAge, intensity = decayRatio.coerceIn(0.0, 1.0))
        }.filter { it.age < maxAge && it.intensity > 0.0 }
    }
}

enum class HazardType {
    RESOURCE_DRAIN,   // Saps bot energy
    CHARGE_SCRAMBLER   // Flips bot charge
}

data class HazardZone(
    val id: String = UUID.randomUUID().toString(),
    val type: HazardType,
    val x: Int,
    val y: Int,
    val radius: Int,
    val name: String,
    val description: String
)

data class SimulationStats(
    val tickCount: Int = 0,
    val totalBots: Int = 0,
    val positiveCount: Int = 0,
    val negativeCount: Int = 0,
    val cloakedCount: Int = 0,
    val averageIntelligence: Double = 0.0,
    val globalResources: Double = 12000.0,
    val highestIntelligence: Int = 1,
    val lastHazardEvent: String = "", // Displays recent hazard interactions
    val isFinished: Boolean = false,
    val posPct: Double = 0.0,
    val negPct: Double = 0.0,
    val boxFilledPct: Double = 0.0,
    val performanceScore: Double = 0.0
)
