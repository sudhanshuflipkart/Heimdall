package com.heimdall.tracker.util

import org.osmdroid.util.GeoPoint

/**
 * Catmull-Rom spline interpolation for GPS route smoothing.
 *
 * Takes a list of raw GPS waypoints and inserts [segmentsPerInterval] interpolated points
 * between each pair of consecutive waypoints, producing a visually smooth path that still
 * passes through every real GPS measurement.
 *
 * Fewer than 3 points → returned as-is (can't compute a spline).
 */
object SplineUtils {

    /**
     * @param points           Raw GPS waypoints from the tracking service.
     * @param segmentsPerGap   Number of synthetic points to insert between each real pair.
     *                         8 gives smooth curves without excessive memory usage.
     */
    fun smooth(points: List<GeoPoint>, segmentsPerGap: Int = 8): List<GeoPoint> {
        if (points.size < 3) return points

        val result = mutableListOf<GeoPoint>()

        // Iterate over each "interior" segment using a 4-point window (p0..p3)
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]

            // Always include the real start point of this segment
            result.add(p1)

            // Insert interpolated points between p1 and p2
            for (j in 1 until segmentsPerGap) {
                val t = j.toDouble() / segmentsPerGap
                val lat = catmullRom(p0.latitude, p1.latitude, p2.latitude, p3.latitude, t)
                val lng = catmullRom(p0.longitude, p1.longitude, p2.longitude, p3.longitude, t)
                result.add(GeoPoint(lat, lng))
            }
        }

        // Include the final real point
        result.add(points.last())

        return result
    }

    /**
     * Catmull-Rom cubic spline formula for a single coordinate dimension.
     * Produces a smooth curve that passes through p1 and p2.
     *
     * @param p0  Control value before the segment start.
     * @param p1  Segment start value (curve passes through this).
     * @param p2  Segment end value (curve passes through this).
     * @param p3  Control value after the segment end.
     * @param t   Interpolation parameter in [0, 1] between p1 and p2.
     */
    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2.0 * p1) +
            (-p0 + p2) * t +
            (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
            (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        )
    }
}
