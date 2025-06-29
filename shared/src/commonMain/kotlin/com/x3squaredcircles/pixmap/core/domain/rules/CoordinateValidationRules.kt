package com.x3squaredcircles.pixmap.shared.domain.rules

import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Business rules for coordinate validation
 */
object CoordinateValidationRules {

    fun isValid(latitude: Double, longitude: Double): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()

        if (latitude < -90 || latitude > 90) {
            errors.add("Latitude $latitude is out of valid range (-90 to 90)")
        }

        if (longitude < -180 || longitude > 180) {
            errors.add("Longitude $longitude is out of valid range (-180 to 180)")
        }

        if (latitude == 0.0 && longitude == 0.0) {
            errors.add("Null Island (0,0) is not a valid location")
        }

        return Pair(errors.isEmpty(), errors)
    }

    fun isValidDistance(from: Coordinate?, to: Coordinate?, maxDistanceKm: Double): Boolean {
        if (from == null || to == null) {
            return false
        }

        val distance = from.distanceTo(to)
        return distance <= maxDistanceKm
    }
}