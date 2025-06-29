package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommand

/**
 * Command to delete a location
 */
data class DeleteLocationCommand(
    val id: Int
) : ICommand