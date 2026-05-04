package com.heimdall.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallDarkGray
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallGoldDark
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel

@Composable
fun SettingsScreen(viewModel: TrackingViewModel) {
    val currentSpeed by viewModel.autoPauseSpeed.collectAsState()
    var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = HeimdallGold,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Auto-pause card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = HeimdallGold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Auto-Pause Threshold",
                            style = MaterialTheme.typography.titleMedium,
                            color = HeimdallWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Timer & distance pause when you slow down",
                            style = MaterialTheme.typography.bodySmall,
                            color = HeimdallWhite.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Current value display
                Text(
                    text = String.format("%.1f m/s  (%.1f km/h)", sliderValue, sliderValue * 3.6),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HeimdallGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Slider: 0.2 to 1.5 m/s
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { viewModel.setAutoPauseSpeed(sliderValue) },
                    valueRange = 0.2f..1.5f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        thumbColor = HeimdallGold,
                        activeTrackColor = HeimdallGold,
                        inactiveTrackColor = HeimdallMediumGray,
                        activeTickColor = HeimdallGoldDark,
                        inactiveTickColor = HeimdallMediumGray
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Nearly stopped",
                        style = MaterialTheme.typography.labelSmall,
                        color = HeimdallWhite.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Brisk walk",
                        style = MaterialTheme.typography.labelSmall,
                        color = HeimdallWhite.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Explanation
                Text(
                    text = "When your speed drops below this for ~15 seconds, " +
                            "the timer and distance freeze automatically. " +
                            "They resume as soon as you start moving again. " +
                            "This keeps your pace stats accurate even if you stop at a shop or traffic light.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HeimdallWhite.copy(alpha = 0.4f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
