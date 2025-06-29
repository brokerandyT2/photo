package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.ICommand

/**
 * Command to create a new location
 */
data class CreateLocationCommand(
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val state: String
) : ICommand<Int>