//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/RestoreLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.mappers.LocationMapper
import kotlinx.coroutines.CancellationException

/**
 * Command to restore a location by its identifier.
 */
data class RestoreLocationCommand(
    val locationId: Int
) : IRequest<Result<LocationDto>>

/**
 * Handles the restoration of a location by its identifier.
 *
 * This class is responsible for processing the [RestoreLocationCommand] to restore
 * a location in the system. It retrieves the location from the data store, invokes its restore operation, and
 * persists the changes. If the location is not found or the update operation fails, a failure result is
 * returned.
 */
class RestoreLocationCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<RestoreLocationCommand, Result<LocationDto>> {

    /**
     * Handles the restoration of a location by its identifier.
     *
     * This method attempts to restore a location by retrieving it from the data store,
     * invoking its restore operation, and saving the changes. If the location is not found or the update operation
     * fails, a failure result is returned. Any exceptions encountered during the process are captured and included
     * in the failure result.
     *
     * @param request The command containing the identifier of the location to restore.
     * @return A [Result] containing a [LocationDto] if the operation succeeds; otherwise, a
     * failure result with an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: RestoreLocationCommand): Result<LocationDto> {
        return try {
            val locationResult = unitOfWork.locations.getByIdAsync(request.locationId)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        locationTitle = "Location ID ${request.locationId}",
                        errorType = LocationErrorType.DatabaseError,
                        errorMessage = "Location not found"
                    )
                )
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!
            location.restore()

            val updateResult = unitOfWork.locations.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        locationTitle = location.title,
                        errorType = LocationErrorType.DatabaseError,
                        errorMessage = updateResult.errorMessage ?: "Update failed"
                    )
                )
                return Result.failure("Failed to update location")
            }

            unitOfWork.saveChangesAsync()

            val locationDto = LocationMapper.run { location.toDto() }
            Result.success(locationDto)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    locationTitle = "Location ID ${request.locationId}",
                    errorType = LocationErrorType.DatabaseError,
                    errorMessage = ex.message ?: "System exception"
                )
            )
            Result.failure("System error occurred: ${ex.message ?: "Unknown error"}")
        }
    }
}