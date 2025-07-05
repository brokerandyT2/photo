// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/WeatherRepositoryAdapter.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.IWeatherRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Weather

/**
 * Adapter that bridges Application IWeatherRepository to Infrastructure WeatherRepository
 */
class WeatherRepositoryAdapter(
    private val innerRepository: WeatherRepository
) : IWeatherRepository {

    override suspend fun getByIdAsync(id: Int): Result<Weather?> {
        return innerRepository.getByIdAsync(id)
    }

    override suspend fun getByLocationIdAsync(locationId: Int): Result<Weather?> {
        return innerRepository.getByLocationIdAsync(locationId)
    }

    override suspend fun addAsync(weather: Weather): Result<Weather> {
        return innerRepository.addAsync(weather)
    }

    override suspend fun updateAsync(weather: Weather): Result<Unit> {
        return innerRepository.updateAsync(weather)
    }

    override suspend fun deleteAsync(id: Int): Result<Boolean> {
        return innerRepository.deleteAsync(id)
    }

    override suspend fun getRecentAsync(count: Int): Result<List<Weather>> {
        return innerRepository.getRecentAsync(count)
    }

    override suspend fun getExpiredAsync(maxAgeHours: Int): Result<List<Weather>> {
        return innerRepository.getExpiredAsync(maxAgeHours)
    }
}