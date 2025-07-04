//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/DeleteLocationCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository

/**
 * Handler for DeleteLocationCommand
 */
class DeleteLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : IRequestHandler<DeleteLocationCommand, Result<Boolean>> {

    override suspend fun handle(request: DeleteLocationCommand): Result<Boolean> {
        return try {
            val location = locationRepository.getById(request.id)
                ?: return Result.failure("Location with id ${request.id} not found")

            location.delete()
            locationRepository.save(location)
            Result.success(true)
        } catch (ex: Exception) {
            Result.failure("Failed to delete location: ${ex.message}")
        }
    }
}