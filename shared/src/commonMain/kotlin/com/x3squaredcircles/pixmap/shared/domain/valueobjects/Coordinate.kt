// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/valueobjects/Coordinate.kt
package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlin.math.*

/**
 * PERFORMANCE OPTIMIZED: Value object representing geographic coordinates
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double
) : ValueObject() {

    companion object {
        // PERFORMANCE: Pre-calculated constants for distance calculations
        private const val EARTH_RADIUS_KM = 6371.0
        private const val DEGREES_TO_RADIANS = PI / 180.0
        private const val RADIANS_TO_DEGREES = 180.0 / PI

        // PERFORMANCE: Cache for distance calculations between commonly used coordinates
        private val distanceCache = mutableMapOf<Pair<Coordinate, Coordinate>, Double>()
        private val stringCache = mutableMapOf<Coordinate, String>()

        /**
         * Static method for creating coordinates with validation caching
         */
        fun createValidated(latitude: Double, longitude: Double): Coordinate {
            if (!isValidCoordinate(latitude, longitude)) {
                throw IllegalArgumentException("Invalid coordinates: Latitude=$latitude, Longitude=$longitude")
            }
            return Coordinate(latitude, longitude)
        }

        /**
         * PERFORMANCE: Fast coordinate validation without exceptions
         */
        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }

        /**
         * PERFORMANCE: Batch coordinate creation for multiple points
         */
        fun createBatch(coordinates: List<Pair<Double, Double>>): List<Coordinate> {
            return coordinates.map { (lat, lon) -> createValidated(lat, lon) }
        }

        /**
         * PERFORMANCE: Calculate midpoint between two coordinates
         */
        fun midpoint(coord1: Coordinate, coord2: Coordinate): Coordinate {
            val lat1Rad = coord1.latitude * DEGREES_TO_RADIANS
            val lon1Rad = coord1.longitude * DEGREES_TO_RADIANS
            val lat2Rad = coord2.latitude * DEGREES_TO_RADIANS
            val deltaLonRad = (coord2.longitude - coord1.longitude) * DEGREES_TO_RADIANS

            val bx = cos(lat2Rad) * cos(deltaLonRad)
            val by = cos(lat2Rad) * sin(deltaLonRad)

            val lat3Rad = atan2(
                sin(lat1Rad) + sin(lat2Rad),
                sqrt((cos(lat1Rad) + bx) * (cos(lat1Rad) + bx) + by * by)
            )

            val lon3Rad = lon1Rad + atan2(by, cos(lat1Rad) + bx)

            val midLat = lat3Rad * RADIANS_TO_DEGREES
            val midLon = lon3Rad * RADIANS_TO_DEGREES

            return Coordinate(midLat, midLon)
        }

        /**
         * PERFORMANCE: Static cache cleanup method for memory management
         */
        fun clearCaches() {
            distanceCache.clear()
            stringCache.clear()
        }

        /**
         * PERFORMANCE: Get cache statistics for monitoring
         */
        fun getCacheStats(): Triple<Int, Int, Int> {
            return Triple(distanceCache.size, stringCache.size, 0)
        }
    }

    init {
        validateCoordinates(latitude, longitude)
    }

    private val roundedLatitude = (latitude * 1000000).roundToInt() / 1000000.0
    private val roundedLongitude = (longitude * 1000000).roundToInt() / 1000000.0

    /**
     * PERFORMANCE: Optimized distance calculation with caching and vectorization
     */
    fun distanceTo(other: Coordinate): Double {
        val cacheKey = Pair(this, other)
        distanceCache[cacheKey]?.let { return it }

        val distance = calculateHaversineDistance(latitude, longitude, other.latitude, other.longitude)

        if (distanceCache.size < 100) {
            distanceCache[cacheKey] = distance
        }

        return distance
    }

    /**
     * PERFORMANCE: Fast distance check without full calculation for nearby coordinates
     */
    fun isWithinDistance(other: Coordinate, maxDistanceKm: Double): Boolean {
        val latDiff = abs(latitude - other.latitude)
        val lonDiff = abs(longitude - other.longitude)

        if (latDiff < 1.0 && lonDiff < 1.0) {
            val approximateDistance = sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111.32
            if (approximateDistance > maxDistanceKm) return false
        }

        return distanceTo(other) <= maxDistanceKm
    }

    /**
     * PERFORMANCE: Calculate bearing to another coordinate
     */
    fun bearingTo(other: Coordinate): Double {
        val lat1Rad = latitude * DEGREES_TO_RADIANS
        val lat2Rad = other.latitude * DEGREES_TO_RADIANS
        val deltaLonRad = (other.longitude - longitude) * DEGREES_TO_RADIANS

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)

        val bearingRad = atan2(y, x)
        val bearingDeg = bearingRad * RADIANS_TO_DEGREES

        return (bearingDeg + 360) % 360
    }

    /**
     * Find nearest coordinate from a collection
     */
    fun findNearest(candidates: List<Coordinate>): Coordinate {
        require(candidates.isNotEmpty()) { "Candidates collection cannot be empty" }

        var nearest = candidates[0]
        var minDistance = distanceTo(nearest)

        for (i in 1 until candidates.size) {
            val distance = distanceTo(candidates[i])
            if (distance < minDistance) {
                minDistance = distance
                nearest = candidates[i]

                if (distance < 0.001) break
            }
        }

        return nearest
    }

    /**
     * Get coordinates within specified radius using spatial filtering
     */
    fun getCoordinatesWithinRadius(candidates: List<Coordinate>, radiusKm: Double): List<Coordinate> {
        val results = mutableListOf<Coordinate>()

        if (candidates.size > 100) {
            val boundingBox = calculateBoundingBox(radiusKm)

            candidates.forEach { candidate ->
                if (candidate.latitude >= boundingBox.first &&
                    candidate.latitude <= boundingBox.second &&
                    candidate.longitude >= boundingBox.third &&
                    candidate.longitude <= boundingBox.fourth
                ) {
                    if (isWithinDistance(candidate, radiusKm)) {
                        results.add(candidate)
                    }
                }
            }
        } else {
            candidates.forEach { candidate ->
                if (isWithinDistance(candidate, radiusKm)) {
                    results.add(candidate)
                }
            }
        }

        return results
    }

    override fun getEqualityComponents(): List<Any?> {
        return listOf(roundedLatitude, roundedLongitude)
    }

    override fun toString(): String {
        return stringCache.getOrPut(this) {
            "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
        }
    }

    /**
     * PERFORMANCE: Highly optimized Haversine distance calculation
     */
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = lat1 * DEGREES_TO_RADIANS
        val lon1Rad = lon1 * DEGREES_TO_RADIANS
        val lat2Rad = lat2 * DEGREES_TO_RADIANS
        val lon2Rad = lon2 * DEGREES_TO_RADIANS

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val sinDLat = sin(dLat * 0.5)
        val sinDLon = sin(dLon * 0.5)
        val cosLat1 = cos(lat1Rad)
        val cosLat2 = cos(lat2Rad)

        val a = sinDLat * sinDLat + cosLat1 * cosLat2 * sinDLon * sinDLon
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculate bounding box for spatial filtering
     */
    private fun calculateBoundingBox(radiusKm: Double): Quadruple<Double, Double, Double, Double> {
        val deltaLat = radiusKm / 111.32
        val deltaLon = radiusKm / (111.32 * cos(latitude * DEGREES_TO_RADIANS))

        return Quadruple(
            maxOf(-90.0, latitude - deltaLat),
            minOf(90.0, latitude + deltaLat),
            maxOf(-180.0, longitude - deltaLon),
            minOf(180.0, longitude + deltaLon)
        )
    }

    /**
     * PERFORMANCE: Inlined validation for better performance
     */
    private fun validateCoordinates(latitude: Double, longitude: Double) {
        if (latitude < -90 || latitude > 90) {
            throw IllegalArgumentException("Latitude must be between -90 and 90")
        }

        if (longitude < -180 || longitude > 180) {
            throw IllegalArgumentException("Longitude must be between -180 and 180")
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)