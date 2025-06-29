// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/IWeatherRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather

/**
 * Repository interface for Weather operations
 */
interface IWeatherRepository {
    suspend fun getByIdAsync(id: Int): Result<Weather?>
    suspend fun getByLocationIdAsync(locationId: Int): Result<Weather?>
    suspend fun addAsync(weather: Weather): Result<Weather>
    suspend fun updateAsync(weather: Weather): Result<Unit>
    suspend fun deleteAsync(id: Int): Result<Boolean>
    suspend fun getRecentAsync(count: Int = 10): Result<List<Weather>>
    suspend fun getExpiredAsync(maxAgeHours: Int): Result<List<Weather>>
}