package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BotSimulationScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotSimulationScreen(
    modifier: Modifier = Modifier,
    viewModel: SimulationViewModel = viewModel()
) {
    // Collect State
    val bots by viewModel.bots.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val speed by viewModel.simulationSpeed.collectAsState()
    val currentResources by viewModel.globalResources.collectAsState()
    val tickCount by viewModel.tickCount.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val selectedBotId by viewModel.selectedBotId.collectAsState()
    val hazards by viewModel.hazards.collectAsState()

    // Config collectors
    val initPop by viewModel.initialPopulation.collectAsState()
    val initRes by viewModel.initialResources.collectAsState()
    val repCost by viewModel.replicationCost.collectAsState()
    val graceDur by viewModel.gracePeriodDuration.collectAsState()
    val baseCost by viewModel.baseSurvivalCost.collectAsState()
    val regrowthRate by viewModel.regrowthRate.collectAsState()
    val maxRes by viewModel.maxResources.collectAsState()

    val selectedBot = remember(bots, selectedBotId) {
        bots.find { it.id == selectedBotId }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Controls, 1: Spawner, 2: Setup

    // Main background styling
    val deepNavyBg = Color(0xFF1A1C1E)
    val cardBg = Color(0xFF2B2930)
    val panelBorder = Color(0xFF49454F)

    BoxWithConstraints(
        modifier = modifier
            .background(deepNavyBg)
            .fillMaxSize()
    ) {
        val width = maxWidth
        val height = maxHeight
        val isExpanded = width > 720.dp

        if (isExpanded) {
            // Landscape/Tablet Split Screen Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: The Game Box Simulation Area
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SimulationBoxHeader(
                        tickCount = tickCount,
                        isPlaying = isPlaying,
                        onTogglePlayback = { viewModel.togglePlayback() },
                        onStepForward = { viewModel.tick() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, panelBorder, RoundedCornerShape(16.dp))
                            .background(Color(0xFF000000))
                    ) {
                        SimulationGridCanvas(
                            bots = bots,
                            hazards = hazards,
                            cols = viewModel.COLS,
                            rows = viewModel.ROWS,
                            selectedBotId = selectedBotId,
                            gracePeriodDuration = graceDur,
                            onCellSelected = { x, y -> viewModel.selectCell(x, y) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ResourceProgressBar(
                        current = currentResources,
                        max = maxRes,
                        growth = regrowthRate
                    )
                }

                // Right Column: Controls, Stats, Inspector
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight()
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, panelBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LiveStatsView(stats = stats, botsCount = bots.size)

                    // Selected Bot Inspector
                    if (selectedBot != null) {
                        BotInspectorCard(
                            bot = selectedBot,
                            baseCost = baseCost,
                            onDecompose = { viewModel.deleteBotManual(it.id) },
                            onCharge = { viewModel.chargeBotManual(it.id) },
                            onReplicate = { viewModel.triggerBotManualReplication(it.id) },
                            onClose = { viewModel.clearSelection() }
                        )
                    } else {
                        EmptyInspectorPlaceholder()
                    }

                    // Dashboard Tabs
                    ControlDashboardTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )

                    when (selectedTab) {
                        0 -> PlaybackSettingsPanel(
                            speed = speed,
                            onSpeedChanged = { viewModel.setSpeed(it) },
                            currentResources = currentResources,
                            onInjectResources = { viewModel.injectResources(it) }
                        )
                        1 -> ManualSpawnerPanel(
                            onSpawnSingle = { charge, isGrace ->
                                // Find any empty cell to place manually
                                viewModel.addManualBot(
                                    (0 until viewModel.COLS).random(),
                                    (0 until viewModel.ROWS).random(),
                                    charge,
                                    isGrace
                                )
                            },
                            onSpawnBatch = { count, charge -> viewModel.addRandomBots(count, charge) },
                            onClearAll = { viewModel.deleteBotManual("") } // passing empty deletes nothing, we can clear in resetting
                        )
                        2 -> SimulationConfigPanel(
                            initPop = initPop,
                            initRes = initRes,
                            repCost = repCost,
                            graceDur = graceDur,
                            baseCost = baseCost,
                            regrowthRate = regrowthRate,
                            onInitPopChange = { viewModel.setInitialPopulation(it) },
                            onInitResChange = { viewModel.setInitialResources(it) },
                            onRepCostChange = { viewModel.setReplicationCost(it) },
                            onGraceDurChange = { viewModel.setGracePeriodDuration(it) },
                            onBaseCostChange = { viewModel.setBaseSurvivalCost(it) },
                            onRegrowthRateChange = { viewModel.setRegrowthRate(it) },
                            onResetSim = { viewModel.resetSimulation() }
                        )
                    }
                }
            }
        } else {
            // Portrait Compact Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SimulationBoxHeader(
                    tickCount = tickCount,
                    isPlaying = isPlaying,
                    onTogglePlayback = { viewModel.togglePlayback() },
                    onStepForward = { viewModel.tick() }
                )

                // Grid Game Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, panelBorder, RoundedCornerShape(16.dp))
                        .background(Color(0xFF000000))
                ) {
                    SimulationGridCanvas(
                        bots = bots,
                        hazards = hazards,
                        cols = viewModel.COLS,
                        rows = viewModel.ROWS,
                        selectedBotId = selectedBotId,
                        gracePeriodDuration = graceDur,
                        onCellSelected = { x, y -> viewModel.selectCell(x, y) }
                    )
                }

                ResourceProgressBar(
                    current = currentResources,
                    max = maxRes,
                    growth = regrowthRate
                )

                LiveStatsView(stats = stats, botsCount = bots.size)

                // Bottom Panel Container of controls + inspector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, panelBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Selected Bot Inspector
                    if (selectedBot != null) {
                        BotInspectorCard(
                            bot = selectedBot,
                            baseCost = baseCost,
                            onDecompose = { viewModel.deleteBotManual(it.id) },
                            onCharge = { viewModel.chargeBotManual(it.id) },
                            onReplicate = { viewModel.triggerBotManualReplication(it.id) },
                            onClose = { viewModel.clearSelection() }
                        )
                    } else {
                        EmptyInspectorPlaceholder()
                    }

                    Divider(color = panelBorder)

                    ControlDashboardTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )

                    when (selectedTab) {
                        0 -> PlaybackSettingsPanel(
                            speed = speed,
                            onSpeedChanged = { viewModel.setSpeed(it) },
                            currentResources = currentResources,
                            onInjectResources = { viewModel.injectResources(it) }
                        )
                        1 -> ManualSpawnerPanel(
                            onSpawnSingle = { charge, isGrace ->
                                viewModel.addManualBot(
                                    (0 until viewModel.COLS).random(),
                                    (0 until viewModel.ROWS).random(),
                                    charge,
                                    isGrace
                                )
                            },
                            onSpawnBatch = { count, charge -> viewModel.addRandomBots(count, charge) },
                            onClearAll = { viewModel.deleteBotManual("") }
                        )
                        2 -> SimulationConfigPanel(
                            initPop = initPop,
                            initRes = initRes,
                            repCost = repCost,
                            graceDur = graceDur,
                            baseCost = baseCost,
                            regrowthRate = regrowthRate,
                            onInitPopChange = { viewModel.setInitialPopulation(it) },
                            onInitResChange = { viewModel.setInitialResources(it) },
                            onRepCostChange = { viewModel.setReplicationCost(it) },
                            onGraceDurChange = { viewModel.setGracePeriodDuration(it) },
                            onBaseCostChange = { viewModel.setBaseSurvivalCost(it) },
                            onRegrowthRateChange = { viewModel.setRegrowthRate(it) },
                            onResetSim = { viewModel.resetSimulation() }
                        )
                    }
                }
            }
        }
    }
}

// Draw the simulation cells
@Composable
fun SimulationGridCanvas(
    bots: List<Bot>,
    hazards: List<HazardZone>,
    cols: Int,
    rows: Int,
    selectedBotId: String?,
    gracePeriodDuration: Int,
    onCellSelected: (Int, Int) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cellWidth = size.width / cols
                    val cellHeight = size.height / rows
                    val xCell = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                    val yCell = (offset.y / cellHeight).toInt().coerceIn(0, rows - 1)
                    onCellSelected(xCell, yCell)
                }
            }
    ) {
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        // 1. Draw subtle mesh lines
        val strokeColor = Color(0xFF49454F).copy(alpha = 0.15f)
        for (i in 0..cols) {
            val x = i * cellWidth
            drawLine(
                color = strokeColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
        for (j in 0..rows) {
            val y = j * cellHeight
            drawLine(
                color = strokeColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        // 1.2 Draw environmental hazard zones
        hazards.forEach { hazard ->
            val rectColor = when(hazard.type) {
                HazardType.RESOURCE_DRAIN -> Color(0xFF9013FE).copy(alpha = 0.12f)
                HazardType.CHARGE_SCRAMBLER -> Color(0xFFF5A623).copy(alpha = 0.12f)
            }
            val hazardStrokeColor = when(hazard.type) {
                HazardType.RESOURCE_DRAIN -> Color(0xFFB046FF).copy(alpha = 0.4f)
                HazardType.CHARGE_SCRAMBLER -> Color(0xFFFFB347).copy(alpha = 0.4f)
            }

            for (dx in -hazard.radius..hazard.radius) {
                for (dy in -hazard.radius..hazard.radius) {
                    val tx = (hazard.x + dx + cols) % cols
                    val ty = (hazard.y + dy + rows) % rows
                    
                    // Draw filled background cell for hazard
                    drawRect(
                        color = rectColor,
                        topLeft = Offset(tx * cellWidth, ty * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )

                    // Draw thin boundary grids for outer cells of the hazard zone
                    if (kotlin.math.abs(dx) == hazard.radius || kotlin.math.abs(dy) == hazard.radius) {
                        drawRect(
                            color = hazardStrokeColor,
                            topLeft = Offset(tx * cellWidth, ty * cellHeight),
                            size = Size(cellWidth, cellHeight),
                            style = Stroke(width = 1f)
                        )
                    }
                }
            }

            // Draw center decor of hazard zone with a small visual core
            val hCenterX = hazard.x * cellWidth + cellWidth / 2f
            val hCenterY = hazard.y * cellHeight + cellHeight / 2f
            
            drawCircle(
                color = hazardStrokeColor.copy(alpha = 0.5f),
                center = Offset(hCenterX, hCenterY),
                radius = minOf(cellWidth, cellHeight) * 0.4f,
                style = Stroke(width = 1.5f)
            )

            // Dynamic text indicator inside center
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = if (hazard.type == HazardType.RESOURCE_DRAIN) 0xFFD0BCFF.toInt() else 0xFFFFB347.toInt()
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    alpha = 150
                }
                val label = if (hazard.type == HazardType.RESOURCE_DRAIN) "DRAIN" else "SCRAM"
                drawText(label, hCenterX, hCenterY + 3.dp.toPx(), paint)
            }
        }

        // 1.5 Draw selected bot memories overlay if available
        if (selectedBotId != null) {
            val sel = bots.find { it.id == selectedBotId }
            if (sel != null) {
                sel.memories.forEach { mem ->
                    val mX = mem.x
                    val mY = mem.y
                    val memCenterX = mX * cellWidth + cellWidth / 2f
                    val memCenterY = mY * cellHeight + cellHeight / 2f
                    val memoryAlpha = (mem.intensity * 0.7f).toFloat().coerceIn(0f, 1f)

                    when (mem.type) {
                        MemoryType.DANGER -> {
                            // High contrast red overlay
                            drawRect(
                                color = Color(0xFFFF5252).copy(alpha = memoryAlpha * 0.35f),
                                topLeft = Offset(mX * cellWidth + 1f, mY * cellHeight + 1f),
                                size = Size(cellWidth - 2f, cellHeight - 2f)
                            )
                            // Red strike marker
                            drawCircle(
                                color = Color(0xFFFF5252).copy(alpha = memoryAlpha),
                                center = Offset(memCenterX, memCenterY),
                                radius = minOf(cellWidth, cellHeight) * 0.2f,
                                style = Stroke(width = 2f)
                            )
                        }
                        MemoryType.RESOURCE -> {
                            // Neon cyan/green forage memory target
                            drawRect(
                                color = Color(0xFF69F0AE).copy(alpha = memoryAlpha * 0.25f),
                                topLeft = Offset(mX * cellWidth + 1f, mY * cellHeight + 1f),
                                size = Size(cellWidth - 2f, cellHeight - 2f)
                            )
                            drawCircle(
                                color = Color(0xFF00E676).copy(alpha = memoryAlpha),
                                center = Offset(memCenterX, memCenterY),
                                radius = minOf(cellWidth, cellHeight) * 0.2f,
                                style = Stroke(width = 1.5f)
                            )
                        }
                        MemoryType.VISITED -> {
                            // Subtle blue dot trail of visited path
                            drawCircle(
                                color = Color(0xFF40C4FF).copy(alpha = memoryAlpha * 0.5f),
                                center = Offset(memCenterX, memCenterY),
                                radius = minOf(cellWidth, cellHeight) * 0.15f
                            )
                        }
                    }
                }
            }
        }

        // 2. Draw our bots
        bots.forEach { bot ->
            val centerX = bot.x * cellWidth + cellWidth / 2f
            val centerY = bot.y * cellHeight + cellHeight / 2f
            val radius = minOf(cellWidth, cellHeight) * 0.42f

            // Pulsing select outline
            if (bot.id == selectedBotId) {
                drawRect(
                    color = Color(0xFFD0BCFF),
                    topLeft = Offset(bot.x * cellWidth, bot.y * cellHeight),
                    size = Size(cellWidth, cellHeight),
                    style = Stroke(width = 3f)
                )
            }

            // Color code charges
            val baseColor = if (!bot.isRevealed) {
                Color(0xFFCAC4D0) // Grey neutral
            } else {
                when (bot.charge) {
                    Charge.POSITIVE -> Color(0xFF4FD8EB) // Neon Blue/Cyan
                    Charge.NEGATIVE -> Color(0xFFFF89B5) // Neon Red/Pink
                }
            }

            // Draw Cloak Shield or glowing outline
            if (!bot.isRevealed) {
                // Glow circle outline for babies
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.25f,
                    style = Stroke(width = 2f)
                )

                // Render partial ring timer of cloaked period
                val ratio = bot.gracePeriod.toFloat() / gracePeriodDuration.toFloat().coerceAtLeast(1f)
                drawArc(
                    color = Color(0xFFD0BCFF),
                    startAngle = -90f,
                    sweepAngle = ratio * 360f,
                    useCenter = false,
                    topLeft = Offset(centerX - radius * 1.2f, centerY - radius * 1.2f),
                    size = Size(radius * 2.4f, radius * 2.4f),
                    style = Stroke(width = 2.5f)
                )
            } else {
                // Standard energy glow
                drawCircle(
                    color = baseColor.copy(alpha = 0.2f),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.35f
                )
            }

            // Draw core bot body
            drawCircle(
                color = baseColor,
                center = Offset(centerX, centerY),
                radius = radius * 0.7f
            )

            // Draw interior element
            if (bot.isRevealed) {
                // Mini interior sign: '+' or '-'
                val innerSize = radius * 0.4f
                val signColor = Color.Black
                if (bot.charge == Charge.POSITIVE) {
                    // Draw '+'
                    drawLine(
                        color = signColor,
                        start = Offset(centerX - innerSize, centerY),
                        end = Offset(centerX + innerSize, centerY),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = signColor,
                        start = Offset(centerX, centerY - innerSize),
                        end = Offset(centerX, centerY + innerSize),
                        strokeWidth = 2.5f
                    )
                } else {
                    // Draw '-'
                    drawLine(
                        color = signColor,
                        start = Offset(centerX - innerSize, centerY),
                        end = Offset(centerX + innerSize, centerY),
                        strokeWidth = 2.5f
                    )
                }
            }

            // Overlay of Intelligence level if greater than 1
            if (bot.intelligence > 1) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = radius * 1.0f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    drawText(
                        bot.intelligence.toString(),
                        centerX,
                        centerY + (radius * 1.1f), // display beneath or inside core
                        paint
                    )
                }
            }
        }
    }
}

// Top Bar Header
@Composable
fun SimulationBoxHeader(
    tickCount: Int,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onStepForward: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(Color(0xFF2B2930))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "NEURAL AUTOMATA",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E2E6),
                fontFamily = FontFamily.Monospace
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFF4FD8EB) else Color(0xFFFF89B5))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Box Simulation v2.4 • Ticks: $tickCount",
                    fontSize = 11.sp,
                    color = Color(0xFFCAC4D0),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Control buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onTogglePlayback,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) Color(0xFFFF89B5) else Color(0xFFD0BCFF),
                    contentColor = if (isPlaying) Color.White else Color(0xFF381E72)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isPlaying) {
                    Row(
                        modifier = Modifier.size(18.dp).padding(vertical = 2.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPlaying) "PAUSE" else "RUN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = onStepForward,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF4A4458),
                    contentColor = Color(0xFFD0BCFF)
                ),
                enabled = !isPlaying,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "Single Tick Step",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Resource Progress HUD
@Composable
fun ResourceProgressBar(
    current: Double,
    max: Double,
    growth: Double
) {
    val progress = (current / max).toFloat().coerceIn(0f, 1f)
    val color = when {
        progress < 0.2f -> Color(0xFFFF89B5) // Pink panic
        progress < 0.5f -> Color(0xFFFFB300) // Amber warn
        else -> Color(0xFFD0BCFF) // Lilac nominal
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(Color(0xFF2B2930))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GLOBAL RESOURCES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E2E6),
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "${current.toInt()} / ${max.toInt()} (${(progress * 100).toInt()}%)",
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color(0xFF1C1B1F)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (current <= 100) "★ EXTINCTION COLLAPSE RUNNING" else "System Active",
                fontSize = 9.sp,
                color = if (current <= 100) Color(0xFFFF89B5) else Color(0xFFCAC4D0),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Regrowth: +${growth.toInt()} / tick",
                fontSize = 9.sp,
                color = Color(0xFFCAC4D0),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Live Statistics
@Composable
fun LiveStatsView(
    stats: SimulationStats,
    botsCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ECOLOGICAL STATS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFCAC4D0),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatItemCard(
                modifier = Modifier.weight(1f),
                title = "POPULATION",
                value = botsCount.toString(),
                accentColor = Color(0xFF4FD8EB),
                subtitle = "${stats.positiveCount} (+), ${stats.negativeCount} (-)"
            )
            StatItemCard(
                modifier = Modifier.weight(1f),
                title = "AVG INTELLIGENCE",
                value = String.format("%.1f", stats.averageIntelligence),
                accentColor = Color(0xFFD0BCFF),
                subtitle = "Top Level: ${stats.highestIntelligence}"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (stats.lastHazardEvent.contains("scrambled", ignoreCase = true)) Color(0xFFF5A623)
                            else if (stats.lastHazardEvent.contains("drained", ignoreCase = true)) Color(0xFF9013FE)
                            else Color.Gray
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stats.lastHazardEvent,
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatItemCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accentColor: Color,
    subtitle: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2930)
        ),
        border = BorderStroke(1.dp, Color(0xFF49454F)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Selected Bot Inspector
@Composable
fun BotInspectorCard(
    bot: Bot,
    baseCost: Double,
    onDecompose: (Bot) -> Unit,
    onCharge: (Bot) -> Unit,
    onReplicate: (Bot) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2930)
        ),
        border = BorderStroke(1.2.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val chargeText = if (bot.charge == Charge.POSITIVE) "(+)" else "(-)"
                    val chargeColor = if (bot.charge == Charge.POSITIVE) Color(0xFF4FD8EB) else Color(0xFFFF89B5)
                    val displayColor = if (bot.isRevealed) chargeColor else Color(0xFFCAC4D0)

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(displayColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BOT DETAILED METRICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Spec row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    InspectorSpecText("Loc", "(${bot.x}, ${bot.y})")
                    InspectorSpecText("Charge", if (bot.charge == Charge.POSITIVE) "Positive (+)" else "Negative (-)")
                    InspectorSpecText("Status", if (bot.isRevealed) "Revealed" else "Cloaked [${bot.gracePeriod} ticks left]")
                    InspectorSpecText("Age", "${bot.age} ticks")
                }
                Column(modifier = Modifier.weight(1f)) {
                    val metabolicRate = bot.getMetabolicRate(baseCost)
                    InspectorSpecText("Energy", "${String.format("%.1f", bot.energy)} / 150")
                    InspectorSpecText("Metabolic rate", "-${String.format("%.1f", metabolicRate)}/tick")
                    InspectorSpecText("Intelligence", "Tier ${bot.intelligence}")
                    InspectorSpecText("Growth XP", "${String.format("%.1f", bot.xp)} / ${String.format("%.0f", bot.levelUpThreshold)}")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cognitive Memory Segment
            Divider(color = Color(0xFF49454F).copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Check warning conditions
            val isInsideVortex = (kotlin.math.abs(bot.x - 8) <= 3 && kotlin.math.abs(bot.y - 8) <= 3)
            val isInsideFluctuator = (kotlin.math.abs(bot.x - 21) <= 3 && kotlin.math.abs(bot.y - 21) <= 3)
            if (isInsideVortex || isInsideFluctuator) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x25FF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (isInsideVortex) "⚠️ ATMOSPHERIC DISTORTION: Sapping Vortex (-4 energy/tick)"
                               else "⚠️ QUANTUM VOLT LEVEL HIGH: 15% polarity scramble probability!",
                        color = Color(0xFFFF89B5),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val maxMemoryCap = 5 + bot.intelligence * 2
                Text(
                    text = "COGNITIVE MEMORY REGISTER (${bot.memories.size} / $maxMemoryCap)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (bot.memories.isEmpty()) {
                Text(
                    text = "No recorded memories yet. Deploy or run bot to let it explore, map resources, and log coordinates.",
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 6.dp)
                ) {
                    bot.memories.sortedByDescending { it.intensity }.forEach { mem ->
                        val (typeColor, typeSymbol) = when (mem.type) {
                            MemoryType.DANGER -> Pair(Color(0xFFFF5252), "DANGER")
                            MemoryType.RESOURCE -> Pair(Color(0xFF69F0AE), "FORAGE")
                            MemoryType.VISITED -> Pair(Color(0xFF40C4FF), "VISITED")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1B1F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(typeColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$typeSymbol at (${mem.x}, ${mem.y})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "${String.format("%.0f%%", mem.intensity * 100)} str",
                                fontSize = 9.sp,
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCharge(bot) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458), contentColor = Color(0xFFD0BCFF)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("CHARGE +50", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = { onReplicate(bot) },
                    enabled = bot.energy >= 40.0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("FORCE SPLIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = { onDecompose(bot) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F), contentColor = Color(0xFFFF89B5)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text("ERASE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun InspectorSpecText(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
        Text(text = value, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun EmptyInspectorPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1B1F).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
            .padding(vertical = 24.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap any grid square to inspect or manage its occupant bot.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Tabs
@Composable
fun ControlDashboardTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = Color(0xFFD0BCFF),
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = Color(0xFFD0BCFF),
                height = 3.dp
            )
        },
        divider = { Divider(color = Color(0xFF49454F)) }
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text("PLAYBACK", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text("SPAWNER", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
        )
        Tab(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            text = { Text("SETUP CONFIG", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
        )
    }
}

// 1. Playback Settings Panel
@Composable
fun PlaybackSettingsPanel(
    speed: Int,
    onSpeedChanged: (Int) -> Unit,
    currentResources: Double,
    onInjectResources: (Double) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "SIMULATION FLOW SPEED",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeedChanged(it.toInt()) },
                valueRange = 1f..15f,
                steps = 13,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD0BCFF),
                    activeTrackColor = Color(0xFFD0BCFF),
                    inactiveTrackColor = Color(0xFF1C1B1F)
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${speed}hz",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0BCFF),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                fontFamily = FontFamily.Monospace
            )
        }

        Divider(color = Color(0xFF49454F))

        Text(
            text = "ECOLOGY MANUAL STIMULATION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFCAC4D0),
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onInjectResources(500.0) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458), contentColor = Color(0xFFD0BCFF)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("+500 STAR FUEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { onInjectResources(2500.0) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("+2.5K FUEL", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// 2. Manual Spawner Panel
@Composable
fun ManualSpawnerPanel(
    onSpawnSingle: (Charge, Boolean) -> Unit,
    onSpawnBatch: (Int, Charge) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "CONSTRUCT NEW BOT IN BOX",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSpawnSingle(Charge.POSITIVE, false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FD8EB), contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("+1 POSITIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { onSpawnSingle(Charge.NEGATIVE, false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF89B5), contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("+1 NEGATIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        Button(
            onClick = { onSpawnSingle(listOf(Charge.POSITIVE, Charge.NEGATIVE).random(), true) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458), contentColor = Color(0xFFCAC4D0)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text("SPAWN CLOAKED BABY", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Divider(color = Color(0xFF49454F))

        Text(
            text = "BATCH COLONY INCUBATION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFCAC4D0),
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSpawnBatch(5, Charge.POSITIVE) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F), contentColor = Color(0xFFE2E2E6)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("SEED 5 POSITIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = { onSpawnBatch(5, Charge.NEGATIVE) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F), contentColor = Color(0xFFE2E2E6)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("SEED 5 NEGATIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// 3. Simulation Config Panel
@Composable
fun SimulationConfigPanel(
    initPop: Int,
    initRes: Double,
    repCost: Double,
    graceDur: Int,
    baseCost: Double,
    regrowthRate: Double,
    onInitPopChange: (Int) -> Unit,
    onInitResChange: (Double) -> Unit,
    onRepCostChange: (Double) -> Unit,
    onGraceDurChange: (Int) -> Unit,
    onBaseCostChange: (Double) -> Unit,
    onRegrowthRateChange: (Double) -> Unit,
    onResetSim: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "SANDBOX REBUILD CONSTRAINTS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD0BCFF),
            fontFamily = FontFamily.Monospace
        )

        // Starting Pop
        ConfigSliderRow(
            label = "Spawn Count",
            value = initPop.toFloat(),
            range = 5f..80f,
            steps = 75,
            displayValue = initPop.toString(),
            onValueChange = { onInitPopChange(it.toInt()) }
        )

        // Starting Res
        ConfigSliderRow(
            label = "Global Fuel",
            value = initRes.toFloat(),
            range = 2000f..35000f,
            steps = 33,
            displayValue = "${(initRes / 1000).toInt()}k",
            onValueChange = { onInitResChange(it.toDouble()) }
        )

        // Replication Cost
        ConfigSliderRow(
            label = "Replication Cost",
            value = repCost.toFloat(),
            range = 30f..120f,
            steps = 18,
            displayValue = repCost.toInt().toString(),
            onValueChange = { onRepCostChange(it.toDouble()) }
        )

        // Grace period tick length
        ConfigSliderRow(
            label = "Grace Cloak",
            value = graceDur.toFloat(),
            range = 10f..100f,
            steps = 18,
            displayValue = "$graceDur ticks",
            onValueChange = { onGraceDurChange(it.toInt()) }
        )

        // Base survival drain rate
        ConfigSliderRow(
            label = "Base Metabolism",
            value = baseCost.toFloat(),
            range = 0.5f..5.0f,
            steps = 90,
            displayValue = String.format("%.1f", baseCost),
            onValueChange = { onBaseCostChange(it.toDouble()) }
        )

        // Growth regrowthRate
        ConfigSliderRow(
            label = "Regrowth / Tick",
            value = regrowthRate.toFloat(),
            range = 0f..50f,
            steps = 50,
            displayValue = "+${regrowthRate.toInt()}",
            onValueChange = { onRegrowthRateChange(it.toDouble()) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onResetSim,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RESTRUCTURE SIMULATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ConfigSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            Text(text = displayValue, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFD0BCFF),
                activeTrackColor = Color(0xFFD0BCFF),
                inactiveTrackColor = Color(0xFF1C1B1F)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
