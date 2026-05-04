package com.heimdall.tracker.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.heimdall.tracker.R
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallWhite

/**
 * Safely converts any drawable (including AdaptiveIconDrawable) to a Bitmap.
 */
private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun PermissionScreen(onRequestPermissions: () -> Unit) {
    val context = LocalContext.current

    // Safely load the app icon — handles AdaptiveIconDrawable on API 26+
    val appIconBitmap = remember {
        try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            drawable?.let { drawableToBitmap(it, 256) }
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        appIconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Heimdall Logo",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "HEIMDALL",
            style = MaterialTheme.typography.headlineLarge,
            color = HeimdallGold,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = HeimdallGold,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Location Permission Required",
            style = MaterialTheme.typography.titleLarge,
            color = HeimdallWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Heimdall needs location access to track your runs. " +
                    "Your location data stays entirely on your device — " +
                    "we never send it anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = HeimdallWhite.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Privacy badge
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = HeimdallGold.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "No background tracking. No cloud uploads.",
            style = MaterialTheme.typography.labelMedium,
            color = HeimdallWhite.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HeimdallGold,
                contentColor = HeimdallBlack
            )
        ) {
            Text(
                text = "Grant Permission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
