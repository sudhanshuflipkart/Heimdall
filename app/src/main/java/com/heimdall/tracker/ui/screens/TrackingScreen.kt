package com.heimdall.tracker.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heimdall.tracker.ui.components.OsmMapView
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallGreen
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallOrange
import com.heimdall.tracker.ui.theme.HeimdallRed
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel

@Composable
fun TrackingScreen(viewModel: TrackingViewModel) {
    val context = LocalContext.current

    val isTracking by viewModel.isTracking.collectAsState()
    val isManuallyPaused by viewModel.isManuallyPaused.collectAsState()
    val isAutoPaused by viewModel.isAutoPaused.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val distanceMeters by viewModel.distanceMeters.collectAsState()
    val activeTimeMillis by viewModel.activeTimeMillis.collectAsState()
    val gpsDisabledEvent by viewModel.gpsDisabledEvent.collectAsState()

    // ── GPS disabled: show toast and offer to open settings ──
    LaunchedEffect(gpsDisabledEvent) {
        if (gpsDisabledEvent) {
            Toast.makeText(
                context,
                "GPS is off. Please enable Location to start tracking.",
                Toast.LENGTH_LONG
            ).show()
            // Open Location Settings so user can enable GPS in one tap
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            viewModel.consumeGpsDisabledEvent()
        }
    }

    // Derived state
    val isPaused = isManuallyPaused || isAutoPaused

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen map ──
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            currentLocation = currentLocation,
            routePoints = routePoints,
            isTracking = isTracking
        )

        // ── Stats overlay (visible while tracking) ──
        AnimatedVisibility(
            visible = isTracking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HeimdallBlack.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        when {
                            isManuallyPaused -> {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Paused",
                                    tint = HeimdallGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PAUSED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HeimdallGold,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            }
                            isAutoPaused -> {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Auto-Paused",
                                    tint = HeimdallOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AUTO-PAUSED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HeimdallOrange,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = "Live",
                                    tint = HeimdallRed,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TRACKING",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HeimdallRed,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }

                    // Timer — ticks every second (powered by coroutine in service)
                    Text(
                        text = viewModel.formatDuration(activeTimeMillis),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = when {
                            isManuallyPaused -> HeimdallGold
                            isAutoPaused -> HeimdallOrange
                            else -> HeimdallWhite
                        },
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Distance + Pace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn(
                            label = "DISTANCE",
                            value = viewModel.formatDistanceKm(distanceMeters),
                            unit = "km"
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(HeimdallMediumGray)
                        )
                        StatColumn(
                            label = "PACE",
                            value = viewModel.formatPaceFromRaw(distanceMeters, activeTimeMillis),
                            unit = "min/km"
                        )
                    }
                }
            }
        }

        // ── Pre-run prompt ──
        if (!isTracking && routePoints.isEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 32.dp, vertical = 64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HeimdallBlack.copy(alpha = 0.85f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "HEIMDALL",
                        style = MaterialTheme.typography.headlineLarge,
                        color = HeimdallGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Press Start to begin your run",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeimdallWhite.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // ── Bottom controls ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            if (!isTracking) {
                // ── START button ──
                FloatingActionButton(
                    onClick = { viewModel.startTracking() },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    containerColor = HeimdallGold,
                    contentColor = HeimdallBlack,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start tracking",
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                // ── PAUSE/RESUME  +  STOP buttons ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume
                    FloatingActionButton(
                        onClick = {
                            if (isManuallyPaused) viewModel.resumeTracking()
                            else viewModel.pauseTracking()
                        },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        containerColor = if (isManuallyPaused) HeimdallGreen else HeimdallGold,
                        contentColor = HeimdallBlack,
                        elevation = FloatingActionButtonDefaults.elevation(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isManuallyPaused) Icons.Default.PlayArrow
                                          else Icons.Default.Pause,
                            contentDescription = if (isManuallyPaused) "Resume" else "Pause",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Stop (finalises + saves)
                    FloatingActionButton(
                        onClick = { viewModel.stopTracking() },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        containerColor = HeimdallRed,
                        contentColor = HeimdallWhite,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop tracking",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HeimdallWhite.copy(alpha = 0.5f),
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = HeimdallWhite
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelMedium,
            color = HeimdallGold
        )
    }
}
