package com.heimdall.tracker.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.heimdall.tracker.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    isTracking: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configure osmdroid once
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidTileCache = context.cacheDir
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
        }
    }

    // FIX #4: Pass mapView to Polyline constructor
    val routePolyline = remember {
        Polyline(mapView).apply {
            outlinePaint.apply {
                color = AndroidColor.rgb(66, 165, 245) // HeimdallRouteBlue
                strokeWidth = 12f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
        }
    }

    // Custom avatar pin marker
    val customMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "You are here"

            try {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_custom_pin)
                if (drawable != null) {
                    val pinSize = 148 // Large avatar pin
                    val bitmap = Bitmap.createBitmap(pinSize, pinSize, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, pinSize, pinSize)
                    drawable.draw(canvas)
                    icon = BitmapDrawable(context.resources, bitmap)
                }
            } catch (e: Exception) {
                // Fall back to default marker icon if custom pin fails
                e.printStackTrace()
            }
        }
    }

    // FIX #3: Proper OSMDroid lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Add overlays once
        if (routePolyline !in mapView.overlays) {
            mapView.overlays.add(routePolyline)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Update route polyline when points change
    LaunchedEffect(routePoints) {
        try {
            routePolyline.setPoints(routePoints)
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Update custom marker position on location change
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            try {
                customMarker.position = loc
                if (customMarker !in mapView.overlays) {
                    mapView.overlays.add(customMarker)
                }
                mapView.controller.animateTo(loc)
                mapView.invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
