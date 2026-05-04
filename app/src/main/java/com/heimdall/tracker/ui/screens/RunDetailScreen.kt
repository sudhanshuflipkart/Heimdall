package com.heimdall.tracker.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.heimdall.tracker.data.db.RunEntity
import com.heimdall.tracker.ui.theme.HeimdallBlack
import com.heimdall.tracker.ui.theme.HeimdallDarkGray
import com.heimdall.tracker.ui.theme.HeimdallGold
import com.heimdall.tracker.ui.theme.HeimdallMediumGray
import com.heimdall.tracker.ui.theme.HeimdallWhite
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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

    // Load the run from DB
    val run = remember { mutableStateOf<RunEntity?>(null) }
    val routePoints = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    LaunchedEffect(runId) {
        val loadedRun = viewModel.getRunById(runId)
        run.value = loadedRun
        loadedRun?.let {
            val lats = it.routeLatitudes.split(",").mapNotNull { s -> s.toDoubleOrNull() }
            val longs = it.routeLongitudes.split(",").mapNotNull { s -> s.toDoubleOrNull() }
            routePoints.value = lats.zip(longs).map { (lat, lng) -> GeoPoint(lat, lng) }
        }
    }

    val currentRun = run.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HeimdallBlack)
    ) {
        // Top bar
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = HeimdallBlack,
                titleContentColor = HeimdallWhite
            )
        )

        // Map showing the route
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
                    }
                }

                // Lifecycle management
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
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

                // Draw route and fit bounds
                LaunchedEffect(points) {
                    try {
                        // Route polyline
                        val polyline = Polyline(mapView).apply {
                            outlinePaint.apply {
                                color = AndroidColor.rgb(66, 165, 245)
                                strokeWidth = 12f
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                                isAntiAlias = true
                            }
                            setPoints(points)
                        }
                        mapView.overlays.clear()
                        mapView.overlays.add(polyline)

                        // Start marker (green)
                        val startMarker = Marker(mapView).apply {
                            position = points.first()
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Start"
                        }
                        mapView.overlays.add(startMarker)

                        // End marker (red)
                        if (points.size > 1) {
                            val endMarker = Marker(mapView).apply {
                                position = points.last()
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Finish"
                            }
                            mapView.overlays.add(endMarker)
                        }

                        // Zoom to fit the route
                        val lats = points.map { it.latitude }
                        val lngs = points.map { it.longitude }
                        val boundingBox = BoundingBox(
                            lats.max(), lngs.max(),
                            lats.min(), lngs.min()
                        )
                        mapView.post {
                            mapView.zoomToBoundingBox(boundingBox, true, 80)
                        }

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
                // No route data
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

        // Stats card at the bottom
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
                        DetailStat(
                            "PACE",
                            "${viewModel.formatPace(r.averagePaceSecondsPerKm)} /km"
                        )
                    }
                }
            }
        }
    }
}

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
