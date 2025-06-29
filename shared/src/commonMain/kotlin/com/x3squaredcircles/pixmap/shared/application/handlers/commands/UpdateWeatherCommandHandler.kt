// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/UpdateWeatherCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateWeatherCommand
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IWeatherService

/**
 * Handler for UpdateWeatherCommand
 */
class UpdateWeatherCommandHandler(
    private val weatherService: IWeatherService
) : IRequestHandler<UpdateWeatherCommand, WeatherDto> {

    override suspend fun handle(request: UpdateWeatherCommand): WeatherDto {
        val result = weatherService.updateWeatherForLocationAsync(request.locationId)

        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            throw RuntimeException("Failed to update weather: ${result}")
        }
    }
}