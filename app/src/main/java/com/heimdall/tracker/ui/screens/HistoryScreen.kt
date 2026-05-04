package com.heimdall.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heimdall.tracker.data.db.RunEntity
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallDarkGray
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallRed
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: TrackingViewModel,
    onRunClick: (Long) -> Unit = {}
) {
    val runs by viewModel.allRuns.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Run History",
            style = MaterialTheme.typography.headlineLarge,
            color = HeimdallGold,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Summary stats
        if (runs.isNotEmpty()) {
            val totalDistance = runs.sumOf { it.distanceMeters }
            val totalRuns = runs.size

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalRuns",
                            style = MaterialTheme.typography.headlineMedium,
                            color = HeimdallWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RUNS",
                            style = MaterialTheme.typography.labelMedium,
                            color = HeimdallWhite.copy(alpha = 0.5f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.formatDistanceKm(totalDistance),
                            style = MaterialTheme.typography.headlineMedium,
                            color = HeimdallWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "TOTAL KM",
                            style = MaterialTheme.typography.labelMedium,
                            color = HeimdallWhite.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (runs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = HeimdallMediumGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No runs yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = HeimdallWhite.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Start your first run to see it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeimdallWhite.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(runs, key = { it.id }) { run ->
                    RunHistoryCard(
                        run = run,
                        viewModel = viewModel,
                        onClick = { onRunClick(run.id) },
                        onDelete = { viewModel.deleteRun(run.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun RunHistoryCard(
    run: RunEntity,
    viewModel: TrackingViewModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(run.dateTimestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelMedium,
                    color = HeimdallWhite.copy(alpha = 0.5f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete run",
                            tint = HeimdallRed.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View route",
                        tint = HeimdallGold.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RunStat(
                    icon = Icons.Default.Route,
                    label = "Distance",
                    value = viewModel.formatDistance(run.distanceMeters)
                )
                RunStat(
                    icon = Icons.Default.Timer,
                    label = "Time",
                    value = viewModel.formatDuration(run.durationMillis)
                )
                RunStat(
                    icon = Icons.Default.Speed,
                    label = "Pace",
                    value = "${viewModel.formatPace(run.averagePaceSecondsPerKm)} /km"
                )
            }
        }
    }
}

@Composable
private fun RunStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = HeimdallGold,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = HeimdallWhite,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HeimdallWhite.copy(alpha = 0.4f),
            fontSize = 10.sp
        )
    }
}
