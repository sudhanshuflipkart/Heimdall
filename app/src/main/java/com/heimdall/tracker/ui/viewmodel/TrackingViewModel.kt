package com.heimdall.tracker.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heimdall.tracker.data.db.HeimdallDatabase
import com.heimdall.tracker.data.db.RunEntity
import com.heimdall.tracker.data.repository.RunRepository
import com.heimdall.tracker.service.TrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RunRepository

    val allRuns: StateFlow<List<RunEntity>>

    // Observe tracking service state
    val isTracking: StateFlow<Boolean> = TrackingService.isTracking
    val isAutoPaused: StateFlow<Boolean> = TrackingService.isAutoPaused
    val routePoints: StateFlow<List<GeoPoint>> = TrackingService.routePoints
    val currentLocation: StateFlow<GeoPoint?> = TrackingService.currentLocation
    val distanceMeters: StateFlow<Double> = TrackingService.distanceMeters
    val activeTimeMillis: StateFlow<Long> = TrackingService.activeTimeMillis

    // Auto-pause speed setting
    private val _autoPauseSpeed = MutableStateFlow(
        TrackingService.getAutoPauseSpeed(application)
    )
    val autoPauseSpeed: StateFlow<Float> = _autoPauseSpeed.asStateFlow()

    init {
        val db = HeimdallDatabase.getInstance(application)
        repository = RunRepository(db.runDao())
        allRuns = repository.allRuns
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    fun setAutoPauseSpeed(speed: Float) {
        _autoPauseSpeed.value = speed
        TrackingService.setAutoPauseSpeed(getApplication(), speed)
    }

    fun startTracking() {
        try {
            TrackingService.resetState()
            val intent = Intent(getApplication(), TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            getApplication<Application>().startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("TrackingVM", "Failed to start tracking service", e)
        }
    }

    fun stopTracking() {
        try {
            // Save the run before stopping
            val points = routePoints.value
            val distance = distanceMeters.value
            val activeTime = activeTimeMillis.value

            if (distance > 10 && activeTime > 5000) { // minimum 10m and 5s to save
                val avgPace = if (distance > 0) {
                    (activeTime / 1000.0) / (distance / 1000.0) // seconds per km
                } else 0.0

                val run = RunEntity(
                    dateTimestamp = System.currentTimeMillis() - activeTime,
                    durationMillis = activeTime,
                    distanceMeters = distance,
                    averagePaceSecondsPerKm = avgPace,
                    routeLatitudes = points.joinToString(",") { it.latitude.toString() },
                    routeLongitudes = points.joinToString(",") { it.longitude.toString() }
                )

                viewModelScope.launch {
                    try {
                        repository.insertRun(run)
                    } catch (e: Exception) {
                        Log.e("TrackingVM", "Failed to save run", e)
                    }
                }
            }

            val intent = Intent(getApplication(), TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP
            }
            getApplication<Application>().startService(intent)
        } catch (e: Exception) {
            Log.e("TrackingVM", "Failed to stop tracking service", e)
        }
    }

    suspend fun getRunById(runId: Long): RunEntity? = repository.getRunById(runId)

    fun deleteRun(runId: Long) {
        viewModelScope.launch {
            repository.deleteRun(runId)
        }
    }

    // ── Formatting helpers ──

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            String.format("%.0f m", meters)
        } else {
            String.format("%.2f km", meters / 1000.0)
        }
    }

    fun formatDistanceKm(meters: Double): String {
        return String.format("%.2f", meters / 1000.0)
    }

    fun formatPace(secondsPerKm: Double): String {
        if (secondsPerKm <= 0 || secondsPerKm.isInfinite() || secondsPerKm.isNaN()) {
            return "--:--"
        }
        val minutes = (secondsPerKm / 60).toInt()
        val seconds = (secondsPerKm % 60).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }

    fun formatPaceFromRaw(distanceMeters: Double, activeMillis: Long): String {
        if (distanceMeters < 10 || activeMillis < 1000) return "--:--"
        val secondsPerKm = (activeMillis / 1000.0) / (distanceMeters / 1000.0)
        return formatPace(secondsPerKm)
    }
}
