package com.heimdall.tracker.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.heimdall.tracker.data.db.RunEntity
import com.heimdall.tracker.ui.components.buildCircularAvatarBitmap
import com.heimdall.tracker.ui.components.buildDrawableBitmap
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallDarkGray
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallGreen
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallRed
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.util.AvatarManager
import com.heimdall.tracker.util.SplineUtils
import com.heimdall.tracker.R
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    runId: Long,
    viewModel: TrackingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val run = remember { mutableStateOf<RunEntity?>(null) }
    val routePoints = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    LaunchedEffect(runId) {
        val loaded = viewModel.getRunById(runId)
        run.value = loaded
        loaded?.let {
            val lats = it.routeLatitudes.split(",").mapNotNull { s -> s.toDoubleOrNull() }
            val lngs = it.routeLongitudes.split(",").mapNotNull { s -> s.toDoubleOrNull() }
            routePoints.value = lats.zip(lngs).map { (lat, lng) -> GeoPoint(lat, lng) }
        }
    }

    val currentRun = run.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
    ) {
        // ── Top bar ──
        TopAppBar(
            title = {
                Text(
                    text = currentRun?.let {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(Date(it.dateTimestamp))
                    } ?: "Run Details"
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = HeimdallWhite
                    )
                }
            },
            actions = {
                // Share button
                currentRun?.let { r ->
                    IconButton(onClick = {
                        shareRun(
                            context = context,
                            run = r,
                            viewModel = viewModel
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share run",
                            tint = HeimdallGold
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = HeimdallBlack,
                titleContentColor = HeimdallWhite
            )
        )

        // ── Map ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val points = routePoints.value

            if (points.isNotEmpty()) {
                val mapView = remember {
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        minZoomLevel = 4.0
                        maxZoomLevel = 20.0
                        zoomController.setVisibility(
                            org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                        )
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        mapView.onPause()
                        mapView.onDetach()
                    }
                }

                LaunchedEffect(points) {
                    try {
                        // ── Smooth route polyline ──
                        val smoothed = SplineUtils.smooth(points)
                        val polyline = Polyline(mapView).apply {
                            outlinePaint.apply {
                                color = AndroidColor.rgb(66, 165, 245)
                                strokeWidth = 12f
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                                isAntiAlias = true
                            }
                            setPoints(smoothed)
                        }
                        mapView.overlays.clear()
                        mapView.overlays.add(polyline)

                        val isLoop = points.size > 1 &&
                            points.first().distanceTo(points.last()) < 50.0

                        if (isLoop) {
                            // ── Loop run: single avatar marker at start/finish ──
                            val avatarBitmap = if (AvatarManager.hasAvatar(context)) {
                                buildCircularAvatarBitmap(AvatarManager.getAvatarFile(context), 96)
                            } else {
                                buildDrawableBitmap(context, R.drawable.ic_custom_pin, 96)
                            }
                            val loopMarker = Marker(mapView).apply {
                                position = points.first()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "Start / Finish"
                                avatarBitmap?.let {
                                    icon = BitmapDrawable(context.resources, it)
                                }
                            }
                            mapView.overlays.add(loopMarker)
                        } else {
                            // ── Point-to-point run: flag at start, avatar at finish ──
                            val startMarker = Marker(mapView).apply {
                                position = points.first()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Start"
                                // Green flag-style icon from Material Icons rendered as bitmap
                                val flagBitmap = buildFlagBitmap(48)
                                flagBitmap?.let { icon = BitmapDrawable(context.resources, it) }
                            }
                            mapView.overlays.add(startMarker)

                            val avatarBitmap = if (AvatarManager.hasAvatar(context)) {
                                buildCircularAvatarBitmap(AvatarManager.getAvatarFile(context), 96)
                            } else {
                                buildDrawableBitmap(context, R.drawable.ic_custom_pin, 96)
                            }
                            val endMarker = Marker(mapView).apply {
                                position = points.last()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "Finish"
                                avatarBitmap?.let {
                                    icon = BitmapDrawable(context.resources, it)
                                }
                            }
                            mapView.overlays.add(endMarker)
                        }

                        // Zoom to fit route
                        val lats = points.map { it.latitude }
                        val lngs = points.map { it.longitude }
                        val bbox = BoundingBox(
                            lats.max(), lngs.max(),
                            lats.min(), lngs.min()
                        )
                        mapView.post { mapView.zoomToBoundingBox(bbox, true, 80) }
                        mapView.invalidate()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No route data available",
                        color = HeimdallWhite.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ── Stats card ──
        currentRun?.let { r ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HeimdallDarkGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = SimpleDateFormat("EEEE, MMM dd yyyy • h:mm a", Locale.getDefault())
                            .format(Date(r.dateTimestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = HeimdallWhite.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailStat("DISTANCE", viewModel.formatDistance(r.distanceMeters))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(HeimdallMediumGray)
                        )
                        DetailStat("TIME", viewModel.formatDuration(r.durationMillis))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(HeimdallMediumGray)
                        )
                        DetailStat("PACE", "${viewModel.formatPace(r.averagePaceSecondsPerKm)} /km")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Share
// ─────────────────────────────────────────────────────────────────────────────

private fun shareRun(
    context: android.content.Context,
    run: RunEntity,
    viewModel: TrackingViewModel
) {
    try {
        val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(run.dateTimestamp))
        val distance = viewModel.formatDistance(run.distanceMeters)
        val time = viewModel.formatDuration(run.durationMillis)
        val pace = "${viewModel.formatPace(run.averagePaceSecondsPerKm)} /km"

        // Build a shareable image card
        val bmp = buildShareBitmap(date, distance, time, pace)
        val shareDir = File(context.cacheDir, "share").also { it.mkdirs() }
        val imgFile = File(shareDir, "run_summary.png")
        FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imgFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "🏃 Just ran $distance in $time (${pace}) on $date — tracked with Heimdall"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your run"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Draws a 900×400 summary card as a [Bitmap] for sharing. */
private fun buildShareBitmap(
    date: String,
    distance: String,
    time: String,
    pace: String
): Bitmap {
    val w = 900; val h = 400
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Background
    canvas.drawColor(AndroidColor.parseColor("#0D0D0D"))

    // Gold accent bar at top
    val accentPaint = Paint().apply { color = AndroidColor.parseColor("#D4A017") }
    canvas.drawRect(0f, 0f, w.toFloat(), 8f, accentPaint)

    // App name
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#D4A017")
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.15f
    }
    canvas.drawText("HEIMDALL", 60f, 80f, appPaint)

    // Date
    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#999999")
        textSize = 28f
    }
    canvas.drawText(date, 60f, 120f, datePaint)

    // Stat labels
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#666666")
        textSize = 26f
        letterSpacing = 0.1f
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 72f
        typeface = Typeface.DEFAULT_BOLD
    }
    val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#D4A017")
        textSize = 26f
    }

    val col1x = 60f; val col2x = 360f; val col3x = 660f
    val labelY = 220f; val valueY = 300f; val unitY = 340f

    canvas.drawText("DISTANCE", col1x, labelY, labelPaint)
    canvas.drawText(distance, col1x, valueY, valuePaint)

    canvas.drawText("TIME", col2x, labelY, labelPaint)
    canvas.drawText(time, col2x, valueY, valuePaint)

    canvas.drawText("PACE", col3x, labelY, labelPaint)
    canvas.drawText(pace, col3x, valueY, valuePaint)

    // Dividers
    val divPaint = Paint().apply { color = AndroidColor.parseColor("#2D2D2D"); strokeWidth = 2f }
    canvas.drawLine(340f, 180f, 340f, 360f, divPaint)
    canvas.drawLine(640f, 180f, 640f, 360f, divPaint)

    return bmp
}

/** Draws a green circle with a flag pole — used as the START marker. */
private fun buildFlagBitmap(size: Int): Bitmap? = try {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Green circle
    paint.color = AndroidColor.parseColor("#4CAF50")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    // White "S" label
    paint.color = AndroidColor.WHITE
    paint.textSize = size * 0.45f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val textY = size / 2f - (paint.descent() + paint.ascent()) / 2
    canvas.drawText("S", size / 2f, textY, paint)
    bmp
} catch (e: Exception) { null }

// ─────────────────────────────────────────────────────────────────────────────
// Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HeimdallWhite.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = HeimdallWhite,
            fontWeight = FontWeight.Bold
        )
    }
}
