// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/DeleteLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteLocationCommand
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException

/**
 * Handler for DeleteLocationCommand
 */
class DeleteLocationCommandHandler(
    private val locationRepository: ILocationRepository,
    private val mediator: IMediator
) : IRequestHandler<DeleteLocationCommand, Result<Boolean>> {

    override suspend fun handle(request: DeleteLocationCommand): Result<Boolean> {
        try {
            val locationResult = locationRepository.getByIdAsync(request.id)

            if (!locationResult.isSuccess || locationResult.data == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        "Location ID ${request.id}",
                        LocationErrorType.DatabaseError,
                        "Location not found"
                    )
                )
                return Result.failure("Location not found")
            }

            val location = locationResult.data!!
            location.delete()

            val updateResult = locationRepository.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        location.title,
                        LocationErrorType.DatabaseError,
                        updateResult.errorMessage ?: "Update failed"
                    )
                )
                return Result.failure("Location update failed")
            }

            return Result.success(true)
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "LOCATION_IN_USE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            "Location ID ${request.id}",
                            LocationErrorType.ValidationError,
                            ex.message ?: "Location is in use"
                        )
                    )
                    return Result.failure("Location is currently in use and cannot be deleted")
                }
                else -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            "Location ID ${request.id}",
                            LocationErrorType.DatabaseError,
                            ex.message ?: "Domain error"
                        )
                    )
                    return Result.failure("Failed to delete location: ${ex.message}")
                }
            }
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    "Location ID ${request.id}",
                    LocationErrorType.DatabaseError,
                    ex.message ?: "Unknown error"
                )
            )
            return Result.failure("Failed to delete location: ${ex.message}")
        }
    }
}