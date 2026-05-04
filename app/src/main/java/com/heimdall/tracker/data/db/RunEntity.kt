package com.heimdall.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single completed run stored in the local database.
 * All data stays on-device — no cloud sync, no telemetry.
 */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateTimestamp: Long,          // epoch millis when the run started
    val durationMillis: Long,         // total elapsed time in ms
    val distanceMeters: Double,       // total distance in meters
    val averagePaceSecondsPerKm: Double, // average pace in seconds per km
    val routeLatitudes: String,       // comma-separated latitude values
    val routeLongitudes: String       // comma-separated longitude values
)
