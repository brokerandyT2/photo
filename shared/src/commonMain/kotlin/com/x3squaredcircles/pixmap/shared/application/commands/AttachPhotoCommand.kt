// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/AttachPhotoCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.dto.LocationDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest

/**
 * Command to attach a photo to a location
 */
data class AttachPhotoCommand(
    val locationId: Int,
    val photoPath: String
) : IRequest<LocationDto>