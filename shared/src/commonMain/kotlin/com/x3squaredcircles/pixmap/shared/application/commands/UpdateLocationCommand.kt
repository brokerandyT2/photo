// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/UpdateLocationCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest

/**
 * Command to update an existing location
 */
data class UpdateLocationCommand(
    val id: Int,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String,
    val photoPath: String? = null
) : IRequest<LocationDto>