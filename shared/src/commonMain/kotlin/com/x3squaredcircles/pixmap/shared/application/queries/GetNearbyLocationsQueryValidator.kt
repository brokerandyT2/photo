// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetNearbyLocationsQueryValidator.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IValidator
import com.x3squaredcircles.pixmap.shared.application.models.ValidationResult
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources

/**
 * Validates the parameters of a [GetNearbyLocationsQuery] to ensure they meet the required constraints.
 *
 * This validator enforces the following rules:
 * - The [latitude] must be between -90 and 90 degrees
 * - The [longitude] must be between -180 and 180 degrees
 * - The [distanceKm] must be greater than 0 and less than or equal to 100 kilometers
 *
 * If any of these rules are violated, a validation error will be generated with an appropriate error message.
 */
class GetNearbyLocationsQueryValidator : IValidator<GetNearbyLocationsQuery> {

    /**
     * Validates the parameters of a query for retrieving nearby locations.
     *
     * This validator ensures that the latitude, longitude, and distance parameters of the
     * query meet the required constraints. Specifically:
     * - Latitude must be between -90 and 90 degrees
     * - Longitude must be between -180 and 180 degrees
     * - Distance must be greater than 0 and not exceed 100 kilometers
     */
    override fun validate(query: GetNearbyLocationsQuery): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate Latitude
        if (query.latitude !in -90.0..90.0) {
            errors.add(AppResources.locationValidationErrorLatitudeRange)
        }

        // Validate Longitude
        if (query.longitude !in -180.0..180.0) {
            errors.add(AppResources.locationValidationErrorLongitudeRange)
        }

        // Validate DistanceKm
        if (query.distanceKm <= 0.0) {
            errors.add(AppResources.locationValidationErrorDistanceRequired)
        } else if (query.distanceKm > 100.0) {
            errors.add(AppResources.locationValidationErrorDistanceMaximum)
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
}
