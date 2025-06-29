// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/domain/rules/CoordinateValidationRules.kt
package com.x3squaredcircles.pixmap.shared.domain.rules

import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Business rules for coordinate validation
 */
object CoordinateValidationRules {

    /**
     * Validates the specified latitude and longitude values and determines if they represent a valid geographic
     * location.
     */
    fun isValid(latitude: Double, longitude: Double, errors: MutableList<String>): Boolean {
        errors.clear()

        if (latitude < -90 || latitude > 90) {
            errors.add("Latitude $latitude is out of valid range (-90 to 90)")
        }

        if (longitude < -180 || longitude > 180) {
            errors.add("Longitude $longitude is out of valid range (-180 to 180)")
        }

        if (latitude == 0.0 && longitude == 0.0) {
            errors.add("Null Island (0,0) is not a valid location")
        }

        return errors.isEmpty()
    }

    /**
     * Determines whether the distance between two coordinates is within the specified maximum distance.
     */
    fun isValidDistance(from: Coordinate, to: Coordinate, maxDistanceKm: Double): Boolean {
        val distance = from.distanceTo(to)
        return distance <= maxDistanceKm
    }
}