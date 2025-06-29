package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommandHandler
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository

/**
 * Handler for UpdateLocationCommand
 */
class UpdateLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : ICommandHandler<UpdateLocationCommand> {

    override suspend fun handle(command: UpdateLocationCommand) {
        val location = locationRepository.getById(command.id)
            ?: throw IllegalArgumentException("Location with id ${command.id} not found")

        location.updateDetails(command.title, command.description)
        locationRepository.save(location)
    }
}