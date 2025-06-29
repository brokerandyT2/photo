// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/UpdateWeatherCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest

/**
 * Command to update weather data for a location
 */
data class UpdateWeatherCommand(
    val locationId: Int
) : IRequest<WeatherDto>