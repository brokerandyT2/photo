package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommand

/**
 * Command to update an existing location
 */
data class UpdateLocationCommand(
    val id: Int,
    val title: String,
    val description: String
) : ICommand