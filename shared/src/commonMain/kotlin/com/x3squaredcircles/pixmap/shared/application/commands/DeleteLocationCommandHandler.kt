//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/DeleteLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException
import kotlinx.coroutines.CancellationException

/**
 * Handles the deletion of a location by its identifier.
 *
 * If the location is not found or the update operation fails, the method returns a
 * failure result with an appropriate error message.
 */
class DeleteLocationCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<DeleteLocationCommand, Result<Boolean>> {

    /**
     * Handles the deletion of a location by its identifier.
     *
     * If the location is not found or the update operation fails, the method returns a
     * failure result with an appropriate error message.
     *
     * @param request The command containing the identifier of the location to delete.
     * @return A [Result] indicating the success or failure of the operation.
     * Returns true if the location was successfully deleted; otherwise, false.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: DeleteLocationCommand): Result<Boolean> {
        return try {
            val locationResult = unitOfWork.locations.getByIdAsync(request.id)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        locationTitle = "Location ID ${request.id}",
                        errorType = LocationErrorType.DatabaseError,
                        errorMessage = "Location not found"
                    )
                )
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!
            location.delete()

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

            Result.success(true)
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "LOCATION_IN_USE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = "Location ID ${request.id}",
                            errorType = LocationErrorType.ValidationError,
                            errorMessage = ex.message ?: "Location in use"
                        )
                    )
                    Result.failure("Cannot delete location - it is in use")
                }
                else -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            locationTitle = "Location ID ${request.id}",
                            errorType = LocationErrorType.DatabaseError,
                            errorMessage = ex.message ?: "Domain exception"
                        )
                    )
                    Result.failure("Failed to delete location: ${ex.message ?: "Unknown error"}")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    locationTitle = "Location ID ${request.id}",
                    errorType = LocationErrorType.DatabaseError,
                    errorMessage = ex.message ?: "System exception"
                )
            )
            Result.failure("System error occurred: ${ex.message ?: "Unknown error"}")
        }
    }
}