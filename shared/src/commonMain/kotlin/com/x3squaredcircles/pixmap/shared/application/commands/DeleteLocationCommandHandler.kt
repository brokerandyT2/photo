// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/DeleteLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException
import kotlinx.coroutines.cancellation.CancellationException

/**
 * Command to delete a location by its identifier.
 */
data class DeleteLocationCommand(
    val id: Int
)

/**
 * Handles the deletion of a location by its identifier.
 *
 * If the location is not found or the update operation fails, the method returns a
 * failure result with an appropriate error message.
 */
class DeleteLocationCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<DeleteLocationCommand, Boolean> {

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
                        message = AppResources.getLocationErrorNotFoundById(request.id),
                        errorType = LocationErrorType.DatabaseError,
                        details = AppResources.locationErrorNotFound
                    )
                )
                return Result.failure(AppResources.locationErrorNotFound)
            }

            val location = locationResult.data
            location.delete()

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

            Result.success(true)
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "LOCATION_IN_USE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = AppResources.getLocationErrorNotFoundById(request.id),
                            errorType = LocationErrorType.ValidationError,
                            details = ex.message ?: "Location in use"
                        )
                    )
                    Result.failure(AppResources.locationErrorCannotDeleteInUse)
                }
                else -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            message = AppResources.getLocationErrorNotFoundById(request.id),
                            errorType = LocationErrorType.DatabaseError,
                            details = ex.message ?: "Domain exception"
                        )
                    )
                    Result.failure(AppResources.getLocationErrorDeleteFailed(ex.message ?: "Domain exception"))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    message = AppResources.getLocationErrorNotFoundById(request.id),
                    errorType = LocationErrorType.DatabaseError,
                    details = ex.message ?: "Unknown error"
                )
            )
            Result.failure(AppResources.getLocationErrorDeleteFailed(ex.message ?: "Unknown error"))
        }
    }
}