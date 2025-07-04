//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetWeatherByLocationIdQuery.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.dto.WeatherDto

/**
 * Query to get weather by location ID
 */
data class GetWeatherByLocationIdQuery(
    val locationId: Int
) : IRequest<WeatherDto?>