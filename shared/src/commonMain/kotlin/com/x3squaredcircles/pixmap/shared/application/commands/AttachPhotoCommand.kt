package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommand

/**
 * Command to attach a photo to a location
 */
data class AttachPhotoCommand(
    val locationId: Int,
    val photoPath: String
) : ICommand