package com.heimdall.tracker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heimdall.tracker.ui.components.buildCircularAvatarBitmap
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallDarkGray
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallGoldDark
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallRed
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.util.AvatarManager
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel

@Composable
fun SettingsScreen(viewModel: TrackingViewModel) {
    val context = LocalContext.current
    val currentSpeed by viewModel.autoPauseSpeed.collectAsState()
    var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    // Track whether we currently have a saved avatar (refreshes after save/delete)
    var hasAvatar by remember { mutableStateOf(AvatarManager.hasAvatar(context)) }

    // Photo picker launcher — no READ_MEDIA permission needed on Android 13+
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = AvatarManager.saveAvatar(context, uri)
            if (saved) hasAvatar = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
            .verticalScroll(rememberScrollState())
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

        // ── Avatar section ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = HeimdallGold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Map Avatar",
                            style = MaterialTheme.typography.titleMedium,
                            color = HeimdallWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your face appears as the pin while you run",
                            style = MaterialTheme.typography.bodySmall,
                            color = HeimdallWhite.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Avatar preview
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(2.dp, HeimdallGold, CircleShape)
                        .background(HeimdallMediumGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasAvatar) {
                        val bmp = remember(hasAvatar) {
                            buildCircularAvatarBitmap(AvatarManager.getAvatarFile(context), 192)
                        }
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Your avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "No avatar",
                            tint = HeimdallMediumGray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tip card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HeimdallMediumGray.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "💡 Tip: For a fun cartoon look, use ChatGPT or Gemini to convert " +
                               "your selfie into a sticker-style illustration first, then upload it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HeimdallWhite.copy(alpha = 0.6f),
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Upload button
                Button(
                    onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HeimdallGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (hasAvatar) "Change Photo" else "Upload Photo",
                        color = HeimdallBlack,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Remove button — only show if avatar exists
                if (hasAvatar) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            AvatarManager.deleteAvatar(context)
                            hasAvatar = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeimdallRed),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, HeimdallRed.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = HeimdallRed
                        )
                        Text("Remove Photo", color = HeimdallRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Auto-pause card ──
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

                Text(
                    text = String.format("%.1f m/s  (%.1f km/h)", sliderValue, sliderValue * 3.6),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HeimdallGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                Row(modifier = Modifier.fillMaxWidth()) {
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

                Text(
                    text = "When your speed drops below this for ~15 seconds, " +
                           "the timer and distance freeze automatically. " +
                           "Those 15 seconds are retroactively removed from your active time. " +
                           "They resume as soon as you start moving again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HeimdallWhite.copy(alpha = 0.4f),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
