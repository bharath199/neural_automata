package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class SimulationViewModel : ViewModel() {

    // Grid Dimensions
    val COLS = 30
    val ROWS = 30

    // Editable Configurations
    private val _initialPopulation = MutableStateFlow(20)
    val initialPopulation: StateFlow<Int> = _initialPopulation.asStateFlow()

    private val _initialResources = MutableStateFlow(12000.0)
    val initialResources: StateFlow<Double> = _initialResources.asStateFlow()

    private val _replicationCost = MutableStateFlow(70.0)
    val replicationCost: StateFlow<Double> = _replicationCost.asStateFlow()

    private val _gracePeriodDuration = MutableStateFlow(30)
    val gracePeriodDuration: StateFlow<Int> = _gracePeriodDuration.asStateFlow()

    private val _baseSurvivalCost = MutableStateFlow(1.0)
    val baseSurvivalCost: StateFlow<Double> = _baseSurvivalCost.asStateFlow()

    private val _regrowthRate = MutableStateFlow(8.0)
    val regrowthRate: StateFlow<Double> = _regrowthRate.asStateFlow()

    private val _maxResources = MutableStateFlow(12000.0)
    val maxResources: StateFlow<Double> = _maxResources.asStateFlow()

    // Interactive State Variables
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _simulationSpeed = MutableStateFlow(4) // Ticks per second, slider of 1..15
    val simulationSpeed: StateFlow<Int> = _simulationSpeed.asStateFlow()

    private val _globalResources = MutableStateFlow(12000.0)
    val globalResources: StateFlow<Double> = _globalResources.asStateFlow()

    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots: StateFlow<List<Bot>> = _bots.asStateFlow()

    private val _selectedBotId = MutableStateFlow<String?>(null)
    val selectedBotId: StateFlow<String?> = _selectedBotId.asStateFlow()

    private val _tickCount = MutableStateFlow(0)
    val tickCount: StateFlow<Int> = _tickCount.asStateFlow()

    private val _stats = MutableStateFlow(SimulationStats())
    val stats: StateFlow<SimulationStats> = _stats.asStateFlow()

    private val _hazards = MutableStateFlow<List<HazardZone>>(emptyList())
    val hazards: StateFlow<List<HazardZone>> = _hazards.asStateFlow()

    private var currentHazardEvent = "No hazard anomalies triggered yet."
    private var simulationJob: Job? = null

    init {
        _hazards.value = listOf(
            HazardZone(
                id = "drain-1",
                type = HazardType.RESOURCE_DRAIN,
                x = 8,
                y = 8,
                radius = 3,
                name = "Sapping Vortex",
                description = "Rapidly saps energy (-5 energy) from bots inside per tick."
            ),
            HazardZone(
                id = "scrambler-1",
                type = HazardType.CHARGE_SCRAMBLER,
                x = 21,
                y = 21,
                radius = 3,
                name = "Quantum Fluctuator",
                description = "Unstable field: 15% polarity scrambling chance per tick."
            )
        )
        resetSimulation()
    }

    fun startSimulation() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        simulationJob = viewModelScope.launch {
            while (_isPlaying.value) {
                tick()
                val delayMs = 1000L / _simulationSpeed.value.coerceAtLeast(1)
                delay(delayMs)
            }
        }
    }

    fun pauseSimulation() {
        _isPlaying.value = false
        simulationJob?.cancel()
        simulationJob = null
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            pauseSimulation()
        } else {
            startSimulation()
        }
    }

    fun setSpeed(speed: Int) {
        _simulationSpeed.value = speed.coerceIn(1, 15)
        // If running, restart the loop to apply speed instantly
        if (_isPlaying.value) {
            pauseSimulation()
            startSimulation()
        }
    }

    fun setInitialPopulation(num: Int) { _initialPopulation.value = num }
    fun setInitialResources(res: Double) {
        _initialResources.value = res
        _maxResources.value = res
        _globalResources.value = res
    }
    fun setReplicationCost(cost: Double) { _replicationCost.value = cost }
    fun setGracePeriodDuration(dur: Int) { _gracePeriodDuration.value = dur }
    fun setBaseSurvivalCost(cost: Double) { _baseSurvivalCost.value = cost }
    fun setRegrowthRate(rate: Double) { _regrowthRate.value = rate }

    fun selectCell(x: Int, y: Int) {
        val bot = _bots.value.find { it.x == x && it.y == y }
        _selectedBotId.value = bot?.id
    }

    fun clearSelection() {
        _selectedBotId.value = null
    }

    fun injectResources(amount: Double) {
        _globalResources.update { minOf(_maxResources.value, it + amount) }
        recalculateStats()
    }

    fun addManualBot(x: Int, y: Int, charge: Charge, isGrace: Boolean) {
        // If the coordinate is occupied, find the nearest empty cell around it or random cell
        var targetX = x
        var targetY = y
        if (isOccupied(targetX, targetY)) {
            val empty = findEmptyNeighbor(targetX, targetY)
            if (empty != null) {
                targetX = empty.first
                targetY = empty.second
            } else {
                // If nearby is full, find any empty cell on the entire grid
                val anyEmpty = findAnyEmptyCell()
                if (anyEmpty != null) {
                    targetX = anyEmpty.first
                    targetY = anyEmpty.second
                } else {
                    return // Grid is completely full
                }
            }
        }

        val newBot = Bot(
            id = UUID.randomUUID().toString(),
            x = targetX,
            y = targetY,
            charge = charge,
            intelligence = 2,
            energy = 40.0,
            gracePeriod = if (isGrace) _gracePeriodDuration.value else 0
        )
        _bots.update { it + newBot }
        recalculateStats()
    }

    fun addRandomBots(count: Int, charge: Charge) {
        var added = 0
        while (added < count) {
            val cell = findAnyEmptyCell() ?: break
            val isGrace = Random.nextBoolean()
            val newBot = Bot(
                id = UUID.randomUUID().toString(),
                x = cell.first,
                y = cell.second,
                charge = charge,
                intelligence = 2,
                energy = 40.0,
                gracePeriod = if (isGrace) _gracePeriodDuration.value else 0
            )
            _bots.update { it + newBot }
            added++
        }
        recalculateStats()
    }

    fun resetSimulation() {
        pauseSimulation()
        _tickCount.value = 0
        _maxResources.value = _initialResources.value
        _globalResources.value = _initialResources.value
        _selectedBotId.value = null

        val newBots = mutableListOf<Bot>()
        val occupied = mutableSetOf<Pair<Int, Int>>()

        val startCount = _initialPopulation.value.coerceIn(2, COLS * ROWS - 1)
        while (newBots.size < startCount) {
            val rx = Random.nextInt(COLS)
            val ry = Random.nextInt(ROWS)
            if (Pair(rx, ry) !in occupied) {
                occupied.add(Pair(rx, ry))
                val charge = if (Random.nextBoolean()) Charge.POSITIVE else Charge.NEGATIVE
                newBots.add(
                    Bot(
                        id = UUID.randomUUID().toString(),
                        x = rx,
                        y = ry,
                        charge = charge,
                        intelligence = 2,
                        energy = 40.0,
                        gracePeriod = 0 // Starting bots are revealed
                    )
                )
            }
        }
        _bots.value = newBots
        recalculateStats()
    }

    fun triggerBotManualReplication(botId: String) {
        val parent = _bots.value.find { it.id == botId } ?: return
        val emptyNeighbor = findEmptyNeighbor(parent.x, parent.y)
        if (emptyNeighbor != null) {
            val childCharge = if (Random.nextBoolean()) Charge.POSITIVE else Charge.NEGATIVE
            val mutation = listOf(-1, 0, 0, 0, 1).random()
            val childIntel = maxOf(1, parent.intelligence + mutation)

            val child = Bot(
                id = UUID.randomUUID().toString(),
                x = emptyNeighbor.first,
                y = emptyNeighbor.second,
                charge = childCharge,
                intelligence = childIntel,
                energy = 40.0,
                gracePeriod = _gracePeriodDuration.value
            )
            parent.energy = maxOf(5.0, parent.energy - _replicationCost.value)
            parent.updateXp(15.0)

            _bots.update { it + child }
            recalculateStats()
        }
    }

    fun chargeBotManual(botId: String) {
        _bots.update { list ->
            list.map {
                if (it.id == botId) {
                    it.copy(energy = (it.energy + 50.0).coerceAtMost(150.0))
                } else it
            }
        }
        recalculateStats()
    }

    fun deleteBotManual(botId: String) {
        _bots.update { list -> list.filterNot { it.id == botId } }
        if (_selectedBotId.value == botId) {
            _selectedBotId.value = null
        }
        recalculateStats()
    }


    // Single Tick of Simulation
    fun tick() {
        val currentBots = _bots.value.toMutableList()
        var currentResources = _globalResources.value
        val survivalCost = _baseSurvivalCost.value

        // 1. Metabolism & Survival Updates
        val survivors = mutableListOf<Bot>()
        var lastHazardMsg = ""

        for (bot in currentBots) {
            // Decay memory list first for this tick
            val maxMemoryAge = 15 + bot.intelligence * 5
            bot.decayMemories(maxMemoryAge)

            val cost = bot.getMetabolicRate(survivalCost)
            var foragedSuccessfully = false
            if (currentResources >= cost) {
                currentResources -= cost
                bot.energy += cost + 1.5
                foragedSuccessfully = true
            }
            bot.energy -= cost
            bot.age += 1
            bot.updateXp(0.1)

            // Collect standard memories
            val maxCap = 5 + bot.intelligence * 2
            if (foragedSuccessfully) {
                bot.addMemory(bot.x, bot.y, MemoryType.RESOURCE, maxCap)
            }

            if (bot.gracePeriod > 0) {
                bot.gracePeriod -= 1
            }

            // Check active hazards
            for (hazard in _hazards.value) {
                val dx = abs(bot.x - hazard.x)
                val dy = abs(bot.y - hazard.y)
                val distCheckX = minOf(dx, COLS - dx)
                val distCheckY = minOf(dy, ROWS - dy)
                if (maxOf(distCheckX, distCheckY) <= hazard.radius) {
                    // Inside area! Record danger memory
                    bot.addMemory(bot.x, bot.y, MemoryType.DANGER, maxCap)
                    when (hazard.type) {
                        HazardType.RESOURCE_DRAIN -> {
                            bot.energy -= 4.0 // extra energy drain
                            if (Random.nextDouble() < 0.2) {
                                lastHazardMsg = "Bot ${bot.id.take(4)} drained by Sapping Vortex!"
                            }
                        }
                        HazardType.CHARGE_SCRAMBLER -> {
                            if (Random.nextDouble() < 0.15) {
                                val oldC = bot.charge
                                val newC = if (oldC == Charge.POSITIVE) Charge.NEGATIVE else Charge.POSITIVE
                                bot.charge = newC
                                lastHazardMsg = "Bot ${bot.id.take(4)} had charge scrambled!"
                            }
                        }
                    }
                }
            }

            if (bot.energy > 0.0 && currentResources > 0.0) {
                survivors.add(bot)
            }
        }

        if (lastHazardMsg.isNotEmpty()) {
            currentHazardEvent = "[Tick ${_tickCount.value + 1}] $lastHazardMsg"
        }

        // Apply resource regrowth
        currentResources = minOf(_maxResources.value, currentResources + _regrowthRate.value)

        // 2. Movement (In random order to prevent update biases)
        val shuffledBots = survivors.shuffled()
        val gridPositions = shuffledBots.associateBy { Pair(it.x, it.y) }.toMutableMap()

        for (bot in shuffledBots) {
            // Find coordinates of best move
            val nextMove = thinkAndMove(bot, gridPositions)
            if (nextMove != Pair(bot.x, bot.y)) {
                gridPositions.remove(Pair(bot.x, bot.y))
                bot.x = nextMove.first
                bot.y = nextMove.second
                gridPositions[Pair(bot.x, bot.y)] = bot
            }
            // Add custom visited memory
            val maxCap = 5 + bot.intelligence * 2
            bot.addMemory(bot.x, bot.y, MemoryType.VISITED, maxCap)
        }

        // 3. Clashes & Death Rule (Any revealed charge in contact with 3+ revealed opposites dies)
        val deadThisTick = mutableSetOf<String>()
        val survivorsAfterClash = gridPositions.values.toList()

        for (b in survivorsAfterClash) {
            if (!b.isRevealed) continue // Cloaked babies are invincible

            var opponentContacts = 0
            // Check 8 neighbors with wrap-around
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = (b.x + dx + COLS) % COLS
                    val ny = (b.y + dy + ROWS) % ROWS
                    val neighbor = gridPositions[Pair(nx, ny)]
                    if (neighbor != null && neighbor.isRevealed && neighbor.charge != b.charge) {
                        opponentContacts++
                    }
                }
            }
            if (opponentContacts >= 3) {
                deadThisTick.add(b.id)
                // Record danger memory of a clash location before dying so others learn info
                b.addMemory(b.x, b.y, MemoryType.DANGER, 5 + b.intelligence * 2)
            }
        }

        // Filter out neutralized bots
        val postDeathList = survivorsAfterClash.filterNot { it.id in deadThisTick }.toMutableList()
        val gridAfterDeath = postDeathList.associateBy { Pair(it.x, it.y) }.toMutableMap()

        // 4. Replication
        val repCost = _replicationCost.value
        val gpDur = _gracePeriodDuration.value
        val children = mutableListOf<Bot>()

        for (bot in postDeathList) {
            if (bot.energy >= repCost) {
                // Find empty orthogonal spot
                val birthSpot = findEmptyOrthogonalSpot(bot.x, bot.y, gridAfterDeath)
                if (birthSpot != null) {
                    val childCharge = if (Random.nextBoolean()) Charge.POSITIVE else Charge.NEGATIVE
                    val mutation = listOf(-1, 0, 0, 0, 1).random()
                    val childIntel = maxOf(1, bot.intelligence + mutation)

                    val child = Bot(
                        id = UUID.randomUUID().toString(),
                        x = birthSpot.first,
                        y = birthSpot.second,
                        charge = childCharge,
                        intelligence = childIntel,
                        energy = 40.0,
                        gracePeriod = gpDur
                    )
                    bot.energy -= repCost
                    bot.updateXp(20.0) // Large XP boost for successful parent replication
                    children.add(child)
                    gridAfterDeath[birthSpot] = child
                }
            }
        }

        // 5. Update State
        val finalBots = postDeathList + children
        _bots.value = finalBots
        _globalResources.value = currentResources
        _tickCount.value += 1

        recalculateStats()
    }

    private fun thinkAndMove(bot: Bot, grid: Map<Pair<Int, Int>, Bot>): Pair<Int, Int> {
        val directions = listOf(
            Pair(0, 0), Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0),
            Pair(1, 1), Pair(-1, -1), Pair(1, -1), Pair(-1, 1)
        )
        val vision = bot.intelligence
        var bestScore = -Double.MAX_VALUE
        var bestMove = Pair(bot.x, bot.y)

        for (dir in directions) {
            val nx = (bot.x + dir.first + COLS) % COLS
            val ny = (bot.y + dir.second + ROWS) % ROWS

            // Skip if occupied by another bot (can only occupy if it is itself at (0,0))
            if (dir != Pair(0, 0) && grid.containsKey(Pair(nx, ny))) {
                continue
            }

            var score = 0.0
            var opponents = 0.0
            var allies = 0.0

            // Apply bots' direct memory recall if any are matching
            for (mem in bot.memories) {
                val dx = abs(nx - mem.x)
                val dy = abs(ny - mem.y)
                val distCheckX = minOf(dx, COLS - dx)
                val distCheckY = minOf(dy, ROWS - dy)
                val dist = maxOf(1, maxOf(distCheckX, distCheckY))

                when (mem.type) {
                    MemoryType.DANGER -> {
                        // Strong penalty for moving near known hazards or old clash cells
                        score -= (120.0 * mem.intensity) / dist
                    }
                    MemoryType.RESOURCE -> {
                        // Strong attraction if low on energy
                        if (bot.energy < 60.0) {
                            val hungerLevel = (60.0 - bot.energy) / 60.0
                            score += (45.0 * mem.intensity * hungerLevel) / dist
                        }
                    }
                    MemoryType.VISITED -> {
                        // Explore-drive: avoid recently visited spots
                        if (dist <= 1) {
                            score -= 6.0 * mem.intensity
                        }
                    }
                }
            }

            // Scan the box area based on vision range
            for (sy in -vision..vision) {
                for (sx in -vision..vision) {
                    val tx = (nx + sx + COLS) % COLS
                    val ty = (ny + sy + ROWS) % ROWS

                    val other = grid[Pair(tx, ty)]
                    if (other != null && other.id != bot.id) {
                        val dist = maxOf(1.0, maxOf(abs(sx), abs(sy)).toDouble())

                        if (other.isRevealed) {
                            if (other.charge != bot.charge) {
                                opponents += 1.0 / dist
                            } else {
                                allies += 1.0 / dist
                            }
                        } else {
                            // If Cloaked child and I'm checking, can I identify them?
                            // No, other bots ignore cloaked charges. They only see neutral
                            score += 0.1 / dist
                        }
                    }
                }
            }

            // High penalty for cells that are dangerous
            if (opponents >= 2.0) {
                score -= 200.0
            }

            // Weighted scores
            score += allies * 5.0
            score -= opponents * 10.0

            // Simple drive to center where resource regrowth center is
            val distToCenter = sqrt((nx - COLS / 2.0).pow(2) + (ny - ROWS / 2.0).pow(2))
            score -= distToCenter * 0.2

            if (score > bestScore) {
                bestScore = score
                bestMove = Pair(nx, ny)
            }
        }

        return bestMove
    }

    private fun findEmptyOrthogonalSpot(x: Int, y: Int, grid: Map<Pair<Int, Int>, Bot>): Pair<Int, Int>? {
        val list = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
        for (dir in list.shuffled()) {
            val nx = (x + dir.first + COLS) % COLS
            val ny = (y + dir.second + ROWS) % ROWS
            if (!grid.containsKey(Pair(nx, ny))) {
                return Pair(nx, ny)
            }
        }
        return null
    }

    private fun findEmptyNeighbor(x: Int, y: Int): Pair<Int, Int>? {
        val grid = _bots.value.associateBy { Pair(it.x, it.y) }
        val directions = listOf(
            Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0),
            Pair(1, 1), Pair(-1, -1), Pair(1, -1), Pair(-1, 1)
        ).shuffled()
        for (dir in directions) {
            val nx = (x + dir.first + COLS) % COLS
            val ny = (y + dir.second + ROWS) % ROWS
            if (!grid.containsKey(Pair(nx, ny))) {
                return Pair(nx, ny)
            }
        }
        return null
    }

    private fun findAnyEmptyCell(): Pair<Int, Int>? {
        val occupied = _bots.value.map { Pair(it.x, it.y) }.toSet()
        if (occupied.size >= COLS * ROWS) return null
        while (true) {
            val rx = Random.nextInt(COLS)
            val ry = Random.nextInt(ROWS)
            val cell = Pair(rx, ry)
            if (cell !in occupied) {
                return cell
            }
        }
    }

    private fun isOccupied(x: Int, y: Int): Boolean {
        return _bots.value.any { it.x == x && it.y == y }
    }

    private fun recalculateStats() {
        val list = _bots.value
        val pos = list.count { it.charge == Charge.POSITIVE && it.isRevealed }
        val neg = list.count { it.charge == Charge.NEGATIVE && it.isRevealed }
        val cloaked = list.count { !it.isRevealed }
        val avgIntel = if (list.isNotEmpty()) list.map { it.intelligence }.average() else 0.0
        val maxIntel = if (list.isNotEmpty()) list.maxOf { it.intelligence } else 1

        _stats.value = SimulationStats(
            tickCount = _tickCount.value,
            totalBots = list.size,
            positiveCount = pos,
            negativeCount = neg,
            cloakedCount = cloaked,
            averageIntelligence = avgIntel,
            globalResources = _globalResources.value,
            highestIntelligence = maxIntel,
            lastHazardEvent = currentHazardEvent
        )

        // Keep selection ID updated (if selected bot died, clear selection)
        if (_selectedBotId.value != null && list.none { it.id == _selectedBotId.value }) {
            _selectedBotId.value = null
        }
    }
}
