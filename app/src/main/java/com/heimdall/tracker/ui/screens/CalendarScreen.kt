package com.heimdall.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heimdall.tracker.data.db.RunEntity
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallDarkGray
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallGoldDark
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallRed
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: TrackingViewModel) {
    val runs by viewModel.allRuns.collectAsState()

    // Current month being displayed
    var displayedMonth by remember {
        mutableStateOf(Calendar.getInstance().also {
            it.set(Calendar.DAY_OF_MONTH, 1)
            it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
        })
    }

    // Group runs by calendar date key "yyyy-MM-dd"
    val runsByDate: Map<String, List<RunEntity>> = remember(runs) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        runs.groupBy { fmt.format(Date(it.dateTimestamp)) }
    }

    // Total active running days
    val totalActiveDays = runsByDate.keys.size
    val totalRuns = runs.size
    val totalDistanceKm = runs.sumOf { it.distanceMeters } / 1000.0
    val currentStreak = computeCurrentStreak(runs)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(48.dp)) }

        // ── Header ──
        item {
            Text(
                text = "Run Calendar",
                style = MaterialTheme.typography.headlineLarge,
                color = HeimdallGold,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Summary strip ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem("$totalRuns", "RUNS")
                    SummaryItem("$totalActiveDays", "RUN DAYS")
                    SummaryItem(String.format("%.1f", totalDistanceKm), "TOTAL KM")
                    SummaryItem("$currentStreak", "DAY STREAK")
                }
            }
        }

        // ── Month navigation ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month / Year header with prev/next arrows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            displayedMonth = (displayedMonth.clone() as Calendar).also {
                                it.add(Calendar.MONTH, -1)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous month",
                                tint = HeimdallGold
                            )
                        }

                        Text(
                            text = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                                .format(displayedMonth.time),
                            style = MaterialTheme.typography.titleLarge,
                            color = HeimdallWhite,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = {
                            val now = Calendar.getInstance()
                            // Don't navigate past the current month
                            val isCurrentMonth = displayedMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                    displayedMonth.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                            if (!isCurrentMonth) {
                                displayedMonth = (displayedMonth.clone() as Calendar).also {
                                    it.add(Calendar.MONTH, 1)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next month",
                                tint = HeimdallGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Day-of-week headers
                    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dayLabels.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = HeimdallWhite.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar grid
                    CalendarGrid(
                        month = displayedMonth,
                        runsByDate = runsByDate,
                        viewModel = viewModel
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun CalendarGrid(
    month: Calendar,
    runsByDate: Map<String, List<RunEntity>>,
    viewModel: TrackingViewModel
) {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = dateFmt.format(Date())

    val firstDay = (month.clone() as Calendar).also {
        it.set(Calendar.DAY_OF_MONTH, 1)
    }
    // Week starts Monday (1=Mon..7=Sun in ISO, but Calendar uses Sun=1)
    // Convert: Calendar.DAY_OF_WEEK Sun=1,Mon=2..Sat=7 → offset to Monday-start grid
    val dayOfWeekSun = firstDay.get(Calendar.DAY_OF_WEEK) // 1=Sun
    val offset = (dayOfWeekSun + 5) % 7  // Mon=0, Tue=1, ... Sun=6

    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = offset + daysInMonth
    val rows = (totalCells + 6) / 7

    val year = month.get(Calendar.YEAR)
    val mon = month.get(Calendar.MONTH)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - offset + 1

                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        val cal = Calendar.getInstance().also {
                            it.set(year, mon, day, 0, 0, 0)
                            it.set(Calendar.MILLISECOND, 0)
                        }
                        val dateKey = dateFmt.format(cal.time)
                        val runsOnDay = runsByDate[dateKey]
                        val hasRun = !runsOnDay.isNullOrEmpty()
                        val isToday = dateKey == today
                        val totalKm = runsOnDay?.sumOf { it.distanceMeters / 1000.0 } ?: 0.0

                        CalendarDayCell(
                            day = day,
                            hasRun = hasRun,
                            isToday = isToday,
                            distanceKm = totalKm,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    hasRun: Boolean,
    isToday: Boolean,
    distanceKm: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Day number circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            hasRun  -> HeimdallGold
                            isToday -> HeimdallMediumGray
                            else    -> androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
                    .then(
                        if (isToday && !hasRun)
                            Modifier.border(1.dp, HeimdallGold, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$day",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        hasRun  -> HeimdallBlack
                        isToday -> HeimdallGold
                        else    -> HeimdallWhite.copy(alpha = 0.6f)
                    },
                    fontWeight = if (hasRun || isToday) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Distance label below the circle — only on run days
            if (hasRun && distanceKm > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (distanceKm >= 10) "${distanceKm.toInt()}k"
                           else String.format("%.1fk", distanceKm),
                    style = MaterialTheme.typography.labelSmall,
                    color = HeimdallGold.copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = HeimdallWhite,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HeimdallWhite.copy(alpha = 0.5f),
            fontSize = 9.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streak calculation
// ─────────────────────────────────────────────────────────────────────────────

/** Returns the number of consecutive days (ending today or yesterday) that have a run. */
private fun computeCurrentStreak(runs: List<RunEntity>): Int {
    if (runs.isEmpty()) return 0
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val runDates = runs.map { fmt.format(Date(it.dateTimestamp)) }.toSortedSet().toList().reversed()

    val today = fmt.format(Date())
    val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))

    // Streak must include today or yesterday to be "current"
    if (runDates.first() != today && runDates.first() != yesterday) return 0

    val cal = Calendar.getInstance()
    if (runDates.first() == yesterday) cal.add(Calendar.DAY_OF_YEAR, -1)

    var streak = 0
    for (dateStr in runDates) {
        val expected = fmt.format(cal.time)
        if (dateStr == expected) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return streak
}
