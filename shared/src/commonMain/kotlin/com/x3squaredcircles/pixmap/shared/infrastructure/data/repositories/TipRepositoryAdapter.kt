// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/TipRepositoryAdapter.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Adapter that bridges Application ITipRepository to Infrastructure TipRepository
 */
class TipRepositoryAdapter(
    private val innerRepository: TipRepository
) : ITipRepository {

    override suspend fun getByIdAsync(id: Int): Result<Tip?> {
        return innerRepository.getByIdAsync(id)
    }

    override suspend fun getAllAsync(): Result<List<Tip>> {
        return innerRepository.getAllAsync()
    }

    override suspend fun getByTypeAsync(tipTypeId: Int): Result<List<Tip>> {
        return innerRepository.getByTypeAsync(tipTypeId)
    }

    override suspend fun createAsync(tip: Tip): Result<Tip> {
        return innerRepository.createAsync(tip)
    }

    override suspend fun updateAsync(tip: Tip): Result<Tip> {
        return innerRepository.updateAsync(tip)
    }

    override suspend fun deleteAsync(id: Int): Result<Boolean> {
        return innerRepository.deleteAsync(id)
    }

    override suspend fun getRandomByTypeAsync(tipTypeId: Int): Result<Tip?> {
        return innerRepository.getRandomByTypeAsync(tipTypeId)
    }
}