package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.commands.AttachPhotoCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommandHandler
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository

/**
 * Handler for AttachPhotoCommand
 */
class AttachPhotoCommandHandler(
    private val locationRepository: ILocationRepository
) : ICommandHandler<AttachPhotoCommand> {

    override suspend fun handle(command: AttachPhotoCommand) {
        val location = locationRepository.getById(command.locationId)
            ?: throw IllegalArgumentException("Location with id ${command.locationId} not found")

        location.attachPhoto(command.photoPath)
        locationRepository.save(location)
    }
}