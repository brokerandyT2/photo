package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommandHandler
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository

/**
 * Handler for DeleteLocationCommand
 */
class DeleteLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : ICommandHandler<DeleteLocationCommand> {

    override suspend fun handle(command: DeleteLocationCommand) {
        val location = locationRepository.getById(command.id)
            ?: throw IllegalArgumentException("Location with id ${command.id} not found")

        location.delete()
        locationRepository.save(location)
    }
}