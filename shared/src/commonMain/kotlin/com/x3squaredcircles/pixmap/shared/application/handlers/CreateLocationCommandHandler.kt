package com.x3squaredcircles.pixmap.shared.application.handlers

import com.x3squaredcircles.pixmap.shared.application.commands.CreateLocationCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommandHandler
import com.x3squaredcircles.pixmap.shared.domain.entities.Location
import com.x3squaredcircles.pixmap.shared.domain.repositories.ILocationRepository
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Address
import com.x3squaredcircles.pixmap.shared.domain.valueobjects.Coordinate

/**
 * Handler for CreateLocationCommand
 */
class CreateLocationCommandHandler(
    private val locationRepository: ILocationRepository
) : ICommandHandler<CreateLocationCommand, Int> {

    override suspend fun handle(command: CreateLocationCommand): Int {
        val coordinate = Coordinate.createValidated(command.latitude, command.longitude)
        val address = Address(command.city, command.state)

        val location = Location(
            title = command.title,
            description = command.description,
            coordinate = coordinate,
            address = address
        )

        val savedLocation = locationRepository.save(location)
        return savedLocation.id
    }
}