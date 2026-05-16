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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * Foreground Service that tracks user location while a run is active.
 *
 * TIMER: A 1-second coroutine keeps the UI clock ticking smoothly — independent of GPS polling rate.
 *
 * BURST MODE: First 20 seconds use 1s GPS updates for accurate initial lock.
 *             After 20s it drops to 5s updates to save battery.
 *
 * AUTO-PAUSE LOGIC:
 *   - Speed is monitored per GPS reading.
 *   - If speed < threshold for [PAUSE_CONSECUTIVE_COUNT] consecutive readings, auto-pause triggers.
 *   - We record [firstSlowReadingTimestamp] — the moment the user first slowed down.
 *   - On trigger, active time is retroactively clipped to that timestamp, removing the detection window.
 *   - Timer and distance stop accumulating while paused.
 *   - Auto-resumes as soon as speed exceeds threshold again.
 *
 * MANUAL PAUSE: User can manually pause/resume via ACTION_PAUSE / ACTION_RESUME.
 *   - Freezes timer and distance (same as auto-pause, but user-initiated).
 *   - Clears any pending auto-pause detection.
 */
class TrackingService : Service() {

    companion object {
        private const val TAG = "TrackingService"
        const val CHANNEL_ID = "heimdall_tracking_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"

        // ── Auto-pause ──
        private const val PAUSE_CONSECUTIVE_COUNT = 3     // readings before auto-pause triggers
        private const val PREFS_NAME = "heimdall_settings"
        private const val KEY_PAUSE_SPEED = "auto_pause_speed"
        const val DEFAULT_PAUSE_SPEED = 0.5f              // m/s

        // ── Burst mode ──
        private const val BURST_DURATION_MS = 20_000L    // 20s of fast GPS on start
        private const val BURST_INTERVAL_MS = 1_000L     // 1s updates during burst
        private const val NORMAL_INTERVAL_MS = 5_000L    // 5s updates normally

        // ── Shared state (observed by ViewModel / UI) ──
        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _isManuallyPaused = MutableStateFlow(false)
        val isManuallyPaused: StateFlow<Boolean> = _isManuallyPaused.asStateFlow()

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

        // ── Internal mutable state ──
        private var lastResumeTimestamp: Long = 0L
        private var accumulatedActiveTime: Long = 0L
        private var lastLocation: Location? = null
        private var slowReadingCount: Int = 0
        private var firstSlowReadingTimestamp: Long = 0L  // When user first started slowing down
        private var autoPauseSpeedThreshold: Float = DEFAULT_PAUSE_SPEED

        fun resetState() {
            _routePoints.value = emptyList()
            _currentLocation.value = null
            _distanceMeters.value = 0.0
            _activeTimeMillis.value = 0L
            _isAutoPaused.value = false
            _isManuallyPaused.value = false
            lastResumeTimestamp = 0L
            accumulatedActiveTime = 0L
            lastLocation = null
            slowReadingCount = 0
            firstSlowReadingTimestamp = 0L
        }

        fun getAutoPauseSpeed(context: Context): Float =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_PAUSE_SPEED, DEFAULT_PAUSE_SPEED)

        fun setAutoPauseSpeed(context: Context, speed: Float) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_PAUSE_SPEED, speed).apply()
            autoPauseSpeedThreshold = speed
        }
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    // Coroutine scope tied to this service instance lifetime
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    private var burstJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            autoPauseSpeedThreshold = getAutoPauseSpeed(this)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            createNotificationChannel()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { processLocation(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> startTracking()
            ACTION_STOP   -> stopTracking()
            ACTION_PAUSE  -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isTracking.value = false
        serviceScope.cancel()
        try { locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) } }
        catch (e: Exception) { Log.e(TAG, "Error in onDestroy", e) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tracking control
    // ─────────────────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startTracking() {
        try {
            _isTracking.value = true
            _isAutoPaused.value = false
            _isManuallyPaused.value = false
            lastResumeTimestamp = System.currentTimeMillis()
            accumulatedActiveTime = 0L
            slowReadingCount = 0
            firstSlowReadingTimestamp = 0L

            startForeground(NOTIFICATION_ID, createNotification())
            startTimerCoroutine()

            // ── Burst mode: high-frequency updates for first 20 seconds ──
            val burstRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                BURST_INTERVAL_MS
            ).apply {
                setMinUpdateIntervalMillis(500L)
                setWaitForAccurateLocation(true)
            }.build()

            fusedLocationClient?.requestLocationUpdates(
                burstRequest, locationCallback!!, Looper.getMainLooper()
            )

            // After burst, switch to normal interval
            burstJob = serviceScope.launch {
                delay(BURST_DURATION_MS)
                if (_isTracking.value) switchToNormalLocationUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
            _isTracking.value = false
            stopSelf()
        }
    }

    private fun pauseTracking() {
        if (!_isTracking.value || _isManuallyPaused.value) return
        _isManuallyPaused.value = true
        // Save time up to this moment
        if (!_isAutoPaused.value && lastResumeTimestamp > 0) {
            accumulatedActiveTime += System.currentTimeMillis() - lastResumeTimestamp
            _activeTimeMillis.value = accumulatedActiveTime
        }
        // Clear auto-pause detection state
        _isAutoPaused.value = false
        slowReadingCount = 0
        firstSlowReadingTimestamp = 0L
    }

    private fun resumeTracking() {
        if (!_isTracking.value || !_isManuallyPaused.value) return
        _isManuallyPaused.value = false
        lastResumeTimestamp = System.currentTimeMillis()
    }

    private fun stopTracking() {
        try {
            // Finalize active time
            if (!_isAutoPaused.value && !_isManuallyPaused.value && lastResumeTimestamp > 0) {
                accumulatedActiveTime += System.currentTimeMillis() - lastResumeTimestamp
                _activeTimeMillis.value = accumulatedActiveTime
            }
            timerJob?.cancel()
            burstJob?.cancel()
            _isTracking.value = false
            _isAutoPaused.value = false
            _isManuallyPaused.value = false
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
            stopSelf()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Location processing
    // ─────────────────────────────────────────────────────────────────────────

    private fun processLocation(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        _currentLocation.value = geoPoint

        // While manually paused, still update position on map but don't process movement
        if (_isManuallyPaused.value) {
            lastLocation = location
            return
        }

        // Speed from consecutive GPS readings
        val speed = lastLocation?.let { prev ->
            val delta = prev.distanceTo(location)
            val timeDelta = (location.time - prev.time) / 1000.0
            if (timeDelta > 0) delta / timeDelta else 0.0
        } ?: 0.0

        // ── Auto-pause detection ──
        if (speed < autoPauseSpeedThreshold) {
            if (slowReadingCount == 0) {
                // First slow reading — note the time. This is the "effective stop" moment.
                firstSlowReadingTimestamp = System.currentTimeMillis()
            }
            slowReadingCount++

            if (slowReadingCount >= PAUSE_CONSECUTIVE_COUNT && !_isAutoPaused.value) {
                _isAutoPaused.value = true
                // Retroactively clip accumulated time to when user first slowed down,
                // removing the ~15s detection window from the active run time.
                if (lastResumeTimestamp > 0 && firstSlowReadingTimestamp > lastResumeTimestamp) {
                    accumulatedActiveTime += firstSlowReadingTimestamp - lastResumeTimestamp
                    accumulatedActiveTime = maxOf(0L, accumulatedActiveTime)
                }
                _activeTimeMillis.value = accumulatedActiveTime
            }
        } else {
            // Moving — reset slow counter
            slowReadingCount = 0
            firstSlowReadingTimestamp = 0L
            if (_isAutoPaused.value) {
                // Auto-resume
                _isAutoPaused.value = false
                lastResumeTimestamp = System.currentTimeMillis()
            }
        }

        // Accumulate distance and route only when actively moving
        if (!_isAutoPaused.value) {
            lastLocation?.let { prev ->
                val delta = prev.distanceTo(location)
                // Filter GPS jitter: ignore < 2m jumps and > 100m teleports
                if (delta in 2.0..100.0) {
                    _distanceMeters.value += delta
                    _routePoints.value = _routePoints.value + geoPoint
                }
            } ?: run {
                _routePoints.value = listOf(geoPoint)  // First point
            }
        }

        lastLocation = location
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer coroutine — ticks every second, independent of GPS
    // ─────────────────────────────────────────────────────────────────────────

    private fun startTimerCoroutine() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _isTracking.value) {
                if (!_isAutoPaused.value && !_isManuallyPaused.value && lastResumeTimestamp > 0) {
                    _activeTimeMillis.value =
                        accumulatedActiveTime + (System.currentTimeMillis() - lastResumeTimestamp)
                }
                delay(1_000L)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Location request management
    // ─────────────────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun switchToNormalLocationUpdates() {
        try {
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
            val normalRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                NORMAL_INTERVAL_MS
            ).apply {
                setMinUpdateIntervalMillis(3_000L)
                setWaitForAccurateLocation(false)
            }.build()
            fusedLocationClient?.requestLocationUpdates(
                normalRequest, locationCallback!!, Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to normal updates", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while Heimdall is tracking your run"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
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
}
