// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/RestoreLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.mappers.LocationMapper
import kotlinx.coroutines.cancellation.CancellationException

/**
 * Command to restore a location by its identifier.
 */
data class RestoreLocationCommand(
    val locationId: Int
)

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
    private val mapper: LocationMapper,
    private val mediator: IMediator
) : IRequestHandler<RestoreLocationCommand, LocationDto> {

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
                        message = AppResources.getLocationErrorNotFoundById(request.locationId),
                        errorType = LocationErrorType.DatabaseError,
                        details = AppResources.locationErrorNotFound
                    )
                )
                return Result.failure(AppResources.locationErrorNotFound)
            }

            val location = locationResult.data
            location.restore()

            val updateResult = unitOfWork.locations.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        message = location.title,
                        errorType = LocationErrorType.DatabaseError,
                        details = updateResult.errorMessage ?: "Update failed"
                    )
                )
                return Result.failure(AppResources.locationErrorUpdateFailed)
            }

            unitOfWork.saveChangesAsync()

            val locationDto = mapper.toDto(location)
            Result.success(locationDto)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    message = AppResources.getLocationErrorNotFoundById(request.locationId),
                    errorType = LocationErrorType.NetworkError,
                    details = ex.message ?: "Unknown error"
                )
            )
            Result.failure(AppResources.getLocationErrorRestoreFailed(ex.message ?: "Unknown error"))
        }
    }
}