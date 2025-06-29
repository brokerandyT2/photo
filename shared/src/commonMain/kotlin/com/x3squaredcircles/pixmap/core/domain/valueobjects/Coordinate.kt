package com.x3squaredcircles.pixmap.shared.domain.valueobjects

import kotlin.math.*

/**
 * PERFORMANCE OPTIMIZED: Value object representing geographic coordinates
 */
class Coordinate(
    latitude: Double,
    longitude: Double,
    skipValidation: Boolean = false
) : ValueObject() {

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val DEGREES_TO_RADIANS = PI / 180.0
        private const val RADIANS_TO_DEGREES = 180.0 / PI

        private val distanceCache = mutableMapOf<String, Double>()
        private val stringCache = mutableMapOf<Pair<Double, Double>, String>()
        private val validationCache = mutableMapOf<Pair<Double, Double>, Boolean>()

        fun createValidated(latitude: Double, longitude: Double): Coordinate {
            val key = Pair(round(latitude * 1_000_000) / 1_000_000, round(longitude * 1_000_000) / 1_000_000)

            val isValid = validationCache.getOrPut(key) {
                isValidCoordinate(latitude, longitude)
            }

            if (!isValid) {
                throw IllegalArgumentException("Invalid coordinates: Latitude=$latitude, Longitude=$longitude")
            }

            return Coordinate(latitude, longitude, skipValidation = true)
        }

        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 && longitude in -180.0..180.0
        }

        fun calculateDistances(from: Coordinate, destinations: List<Coordinate>): DoubleArray {
            return destinations.map { from.distanceTo(it) }.toDoubleArray()
        }

        fun midpoint(coord1: Coordinate, coord2: Coordinate): Coordinate {
            val lat1Rad = coord1.latitude * DEGREES_TO_RADIANS
            val lon1Rad = coord1.longitude * DEGREES_TO_RADIANS
            val lat2Rad = coord2.latitude * DEGREES_TO_RADIANS
            val deltaLonRad = (coord2.longitude - coord1.longitude) * DEGREES_TO_RADIANS

            val bx = cos(lat2Rad) * cos(deltaLonRad)
            val by = cos(lat2Rad) * sin(deltaLonRad)

            val lat3Rad = atan2(
                sin(lat1Rad) + sin(lat2Rad),
                sqrt((cos(lat1Rad) + bx).pow(2) + by.pow(2))
            )

            val lon3Rad = lon1Rad + atan2(by, cos(lat1Rad) + bx)

            val midLat = lat3Rad * RADIANS_TO_DEGREES
            val midLon = lon3Rad * RADIANS_TO_DEGREES

            return Coordinate(midLat, midLon, skipValidation = true)
        }

        fun clearCaches() {
            distanceCache.clear()
            stringCache.clear()
            validationCache.clear()
        }

        fun getCacheStats(): Triple<Int, Int, Int> {
            return Triple(distanceCache.size, stringCache.size, validationCache.size)
        }
    }

    val latitude: Double
    val longitude: Double
    private val preCalculatedHashCode: Int

    init {
        if (!skipValidation) {
            validateCoordinates(latitude, longitude)
        }
        this.latitude = round(latitude * 1_000_000) / 1_000_000
        this.longitude = round(longitude * 1_000_000) / 1_000_000
        this.preCalculatedHashCode = calculateHashCode(this.latitude, this.longitude)
    }

    private fun validateCoordinates(latitude: Double, longitude: Double) {
        if (latitude !in -90.0..90.0) {
            throw IllegalArgumentException("Latitude must be between -90 and 90")
        }
        if (longitude !in -180.0..180.0) {
            throw IllegalArgumentException("Longitude must be between -180 and 180")
        }
    }

    fun distanceTo(other: Coordinate): Double {
        val cacheKey = "${latitude},${longitude},${other.latitude},${other.longitude}"

        return distanceCache.getOrPut(cacheKey) {
            calculateHaversineDistance(latitude, longitude, other.latitude, other.longitude)
        }
    }

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

    fun isWithinDistance(other: Coordinate, maxDistanceKm: Double): Boolean {
        val latDiff = abs(latitude - other.latitude)
        val lonDiff = abs(longitude - other.longitude)

        if (latDiff < 1.0 && lonDiff < 1.0) {
            val approximateDistance = sqrt(latDiff.pow(2) + lonDiff.pow(2)) * 111.32
            if (approximateDistance > maxDistanceKm) return false
        }

        return distanceTo(other) <= maxDistanceKm
    }

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

    fun getCoordinatesWithinRadius(candidates: List<Coordinate>, radiusKm: Double): List<Coordinate> {
        val results = mutableListOf<Coordinate>()

        if (candidates.size > 100) {
            val boundingBox = calculateBoundingBox(radiusKm)

            candidates.forEach { candidate ->
                if (candidate.latitude >= boundingBox.first &&
                    candidate.latitude <= boundingBox.second &&
                    candidate.longitude >= boundingBox.third &&
                    candidate.longitude <= boundingBox.fourth) {

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

    private fun calculateBoundingBox(radiusKm: Double): Tuple4<Double, Double, Double, Double> {
        val deltaLat = radiusKm / 111.32
        val deltaLon = radiusKm / (111.32 * cos(latitude * DEGREES_TO_RADIANS))

        return Tuple4(
            maxOf(-90.0, latitude - deltaLat),
            minOf(90.0, latitude + deltaLat),
            maxOf(-180.0, longitude - deltaLon),
            minOf(180.0, longitude + deltaLon)
        )
    }

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

    override fun getEqualityComponents(): List<Any?> {
        return listOf(latitude, longitude)
    }

    override fun hashCode(): Int = preCalculatedHashCode

    private fun calculateHashCode(latitude: Double, longitude: Double): Int {
        var result = 17
        result = result * 23 + latitude.hashCode()
        result = result * 23 + longitude.hashCode()
        return result
    }

    override fun toString(): String {
        val key = Pair(latitude, longitude)
        return stringCache.getOrPut(key) {
            "%.6f, %.6f".format(latitude, longitude)
        }
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)