// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/DeleteLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ILocationRepository

/**
 * Handler for DeleteLocationCommand
 */
class DeleteLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<DeleteLocationCommand, Boolean> {

    override suspend fun handle(request: DeleteLocationCommand): Boolean {
        val existingResult = locationRepository.getByIdAsync(request.id)

        if (!existingResult.isSuccess) {
            throw RuntimeException("Failed to get location: ${existingResult}")
        }

        val location = existingResult.getOrNull()
            ?: throw IllegalArgumentException("Location with ID ${request.id} not found")

        location.delete()

        val result = locationRepository.updateAsync(location)

        return if (result.isSuccess) {
            true
        } else {
            throw RuntimeException("Failed to delete location: ${result}")
        }
    }
}