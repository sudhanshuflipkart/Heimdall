package com.heimdall.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.heimdall.tracker.MainActivity
import com.heimdall.tracker.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint

/**
 * Foreground Service that tracks user location ONLY while explicitly running.
 *
 * AUTO-PAUSE LOGIC:
 * - Monitors speed from GPS readings
 * - If speed < threshold for [PAUSE_CONSECUTIVE_COUNT] consecutive readings (~15s), auto-pauses
 * - Timer and distance stop accumulating while paused
 * - Auto-resumes as soon as speed exceeds threshold
 * - Paused time is excluded from pace calculations
 */
class TrackingService : Service() {

    companion object {
        private const val TAG = "TrackingService"
        const val CHANNEL_ID = "heimdall_tracking_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        // Auto-pause settings
        private const val PAUSE_CONSECUTIVE_COUNT = 3 // readings before auto-pause triggers
        private const val PREFS_NAME = "heimdall_settings"
        private const val KEY_PAUSE_SPEED = "auto_pause_speed"
        const val DEFAULT_PAUSE_SPEED = 0.5f // m/s

        // Shared state observed by the UI
        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _isAutoPaused = MutableStateFlow(false)
        val isAutoPaused: StateFlow<Boolean> = _isAutoPaused.asStateFlow()

        private val _routePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
        val routePoints: StateFlow<List<GeoPoint>> = _routePoints.asStateFlow()

        private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
        val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

        private val _distanceMeters = MutableStateFlow(0.0)
        val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

        private val _activeTimeMillis = MutableStateFlow(0L)
        val activeTimeMillis: StateFlow<Long> = _activeTimeMillis.asStateFlow()

        private var lastResumeTimestamp: Long = 0L
        private var accumulatedActiveTime: Long = 0L
        private var lastLocation: Location? = null
        private var slowReadingCount: Int = 0
        private var autoPauseSpeedThreshold: Float = DEFAULT_PAUSE_SPEED

        fun resetState() {
            _routePoints.value = emptyList()
            _currentLocation.value = null
            _distanceMeters.value = 0.0
            _activeTimeMillis.value = 0L
            _isAutoPaused.value = false
            lastResumeTimestamp = 0L
            accumulatedActiveTime = 0L
            lastLocation = null
            slowReadingCount = 0
        }

        /** Read the saved auto-pause speed from SharedPreferences */
        fun getAutoPauseSpeed(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_PAUSE_SPEED, DEFAULT_PAUSE_SPEED)
        }

        /** Save the auto-pause speed to SharedPreferences */
        fun setAutoPauseSpeed(context: Context, speed: Float) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(KEY_PAUSE_SPEED, speed).apply()
            autoPauseSpeedThreshold = speed
        }
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            autoPauseSpeedThreshold = getAutoPauseSpeed(this)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            createNotificationChannel()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        _currentLocation.value = geoPoint

                        // Calculate speed from consecutive points
                        val speed = lastLocation?.let { prev ->
                            val delta = prev.distanceTo(location)
                            val timeDelta = (location.time - prev.time) / 1000.0 // seconds
                            if (timeDelta > 0) delta / timeDelta else 0.0
                        } ?: 0.0

                        // ── Auto-pause logic ──
                        if (speed < autoPauseSpeedThreshold) {
                            slowReadingCount++
                            if (slowReadingCount >= PAUSE_CONSECUTIVE_COUNT && !_isAutoPaused.value) {
                                // Trigger auto-pause
                                _isAutoPaused.value = true
                                // Save active time accumulated so far
                                if (lastResumeTimestamp > 0) {
                                    accumulatedActiveTime += System.currentTimeMillis() - lastResumeTimestamp
                                }
                                _activeTimeMillis.value = accumulatedActiveTime
                            }
                        } else {
                            slowReadingCount = 0
                            if (_isAutoPaused.value) {
                                // Auto-resume
                                _isAutoPaused.value = false
                                lastResumeTimestamp = System.currentTimeMillis()
                            }
                        }

                        // Only accumulate distance & route points when NOT paused
                        if (!_isAutoPaused.value) {
                            lastLocation?.let { prev ->
                                val delta = prev.distanceTo(location)
                                // Filter GPS jitter — ignore < 2m or > 100m jumps
                                if (delta in 2.0..100.0) {
                                    _distanceMeters.value += delta
                                    _routePoints.value = _routePoints.value + geoPoint
                                }
                            } ?: run {
                                // First point
                                _routePoints.value = listOf(geoPoint)
                            }

                            // Update active time
                            if (lastResumeTimestamp > 0) {
                                _activeTimeMillis.value = accumulatedActiveTime +
                                        (System.currentTimeMillis() - lastResumeTimestamp)
                            }
                        }

                        lastLocation = location
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_NOT_STICKY
    }

    @Suppress("MissingPermission")
    private fun startTracking() {
        try {
            _isTracking.value = true
            _isAutoPaused.value = false
            lastResumeTimestamp = System.currentTimeMillis()
            accumulatedActiveTime = 0L
            slowReadingCount = 0

            startForeground(NOTIFICATION_ID, createNotification())

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L // 5-second polling interval
            ).apply {
                setMinUpdateIntervalMillis(3000L)
                setWaitForAccurateLocation(true)
            }.build()

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
            _isTracking.value = false
            stopSelf()
        }
    }

    private fun stopTracking() {
        try {
            // Finalize active time
            if (!_isAutoPaused.value && lastResumeTimestamp > 0) {
                accumulatedActiveTime += System.currentTimeMillis() - lastResumeTimestamp
                _activeTimeMillis.value = accumulatedActiveTime
            }

            _isTracking.value = false
            _isAutoPaused.value = false
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while Heimdall is tracking your run"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isTracking.value = false
        try {
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}
