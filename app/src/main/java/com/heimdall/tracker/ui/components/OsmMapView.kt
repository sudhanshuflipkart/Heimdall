package com.heimdall.tracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.heimdall.tracker.R
import com.heimdall.tracker.util.AvatarManager
import com.heimdall.tracker.util.SplineUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Composable OSMDroid map used during live tracking.
 *
 * Flicker fix: the camera only animates to the new location when the map has been laid
 * out at least once ([mapReady] flag). This prevents the on/off blink caused by animateTo
 * being called before the MapView has a valid size.
 *
 * Smooth curves: route points are passed through a Catmull-Rom spline before being
 * handed to the Polyline overlay, so the drawn path looks smooth even when GPS waypoints
 * are sparse.
 *
 * Avatar pin: the live position marker uses the user's custom photo (if set in Settings)
 * or falls back to the bundled ic_custom_pin drawable.
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    isTracking: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Flicker fix: don't animate camera until the view has been laid out
    var mapReady by remember { mutableStateOf(false) }

    // Configure osmdroid once per process
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidTileCache = context.cacheDir
        }
    }

    // Create the MapView once and keep it stable
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            // Disable the built-in zoom buttons (we use pinch)
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
        }
    }

    val routePolyline = remember {
        Polyline(mapView).apply {
            outlinePaint.apply {
                color = AndroidColor.rgb(66, 165, 245)
                strokeWidth = 12f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
        }
    }

    // Build the avatar marker icon — custom photo or fallback SVG pin
    val markerBitmap: Bitmap? = remember {
        val avatarFile = AvatarManager.getAvatarFile(context)
        if (avatarFile.exists()) {
            // User has set a custom photo
            buildCircularAvatarBitmap(avatarFile, 148)
        } else {
            // Fallback: bundled drawable
            buildDrawableBitmap(context, R.drawable.ic_custom_pin, 148)
        }
    }

    val positionMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "You are here"
            markerBitmap?.let { icon = BitmapDrawable(context.resources, it) }
        }
    }

    // Lifecycle management — resume/pause/detach the MapView properly
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else                      -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (routePolyline !in mapView.overlays) mapView.overlays.add(routePolyline)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Update route polyline with smoothed points
    LaunchedEffect(routePoints) {
        try {
            val smoothed = SplineUtils.smooth(routePoints)
            routePolyline.setPoints(smoothed)
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Update marker and animate camera — only after layout is ready (flicker fix)
    LaunchedEffect(currentLocation, mapReady) {
        if (!mapReady) return@LaunchedEffect
        currentLocation?.let { loc ->
            try {
                positionMarker.position = loc
                if (positionMarker !in mapView.overlays) {
                    mapView.overlays.add(positionMarker)
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
        modifier = modifier,
        update = { view ->
            // Flicker fix: set mapReady on the first real layout pass
            if (!mapReady && view.width > 0 && view.height > 0) {
                mapReady = true
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Bitmap helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Load a drawable resource as a [Bitmap] at the given size. */
fun buildDrawableBitmap(
    context: android.content.Context,
    drawableRes: Int,
    size: Int
): Bitmap? = try {
    val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    bmp
} catch (e: Exception) {
    null
}

/**
 * Decode an image file, crop to a circle, and resize to [size]×[size].
 * Used to show the user's custom avatar as the map pin.
 */
fun buildCircularAvatarBitmap(file: File, size: Int): Bitmap? = try {
    val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    // Scale to square
    val scaled = Bitmap.createScaledBitmap(raw, size, size, true)
    // Punch out a circle
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaled, 0f, 0f, paint)
    output
} catch (e: Exception) {
    null
}
