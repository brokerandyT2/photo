// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/DeleteLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteLocationCommand
import com.x3squaredcircles.pixmap.shared.application.events.LocationSaveErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.LocationErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.common.Result
import com.x3squaredcircles.pixmap.shared.domain.exceptions.LocationDomainException

/**
 * Handler for DeleteLocationCommand
 */
class DeleteLocationCommandHandler(
    private val locationRepository: ILocationRepository,
    private val mediator: IMediator
) : IRequestHandler<DeleteLocationCommand, Boolean> {

    override suspend fun handle(request: DeleteLocationCommand): Boolean {
        try {
            val locationResult = locationRepository.getByIdAsync(request.id)

            if (!locationResult.isSuccess || locationResult.getOrNull() == null) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        "Location ID ${request.id}",
                        LocationErrorType.DATABASE_ERROR,
                        "Location not found"
                    )
                )
                throw IllegalArgumentException("Location not found")
            }

            val location = locationResult.getOrThrow()
            location.delete()

            val updateResult = locationRepository.updateAsync(location)
            if (!updateResult.isSuccess) {
                mediator.publish(
                    LocationSaveErrorEvent(
                        location.title,
                        LocationErrorType.DATABASE_ERROR,
                        updateResult.exceptionOrNull()?.message ?: "Update failed"
                    )
                )
                throw RuntimeException("Location update failed")
            }

            return true
        } catch (ex: LocationDomainException) {
            when (ex.code) {
                "LOCATION_IN_USE" -> {
                    mediator.publish(
                        LocationSaveErrorEvent(
                            "Location ID ${request.id}",
                            LocationErrorType.VALIDATION_ERROR,
                            ex.message ?: "Location is in use"
                        )
                    )
                    throw IllegalStateException("Cannot delete location that is in use")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                LocationSaveErrorEvent(
                    "Location ID ${request.id}",
                    LocationErrorType.DATABASE_ERROR,
                    ex.message ?: "Delete operation failed"
                )
            )
            throw RuntimeException("Failed to delete location: ${ex.message}", ex)
        }
    }
}