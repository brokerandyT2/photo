// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/data/repositories/TipTypeRepositoryAdapter.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.data.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Adapter that bridges Application ITipTypeRepository to Infrastructure TipTypeRepository
 */
class TipTypeRepositoryAdapter(
    private val innerRepository: TipTypeRepository
) : ITipTypeRepository {

    override suspend fun getByIdAsync(id: Int): Result<TipType?> {
        return innerRepository.getByIdAsync(id)
    }

    override suspend fun getAllAsync(): Result<List<TipType>> {
        return innerRepository.getAllAsync()
    }

    override suspend fun addAsync(tipType: TipType): Result<TipType> {
        return innerRepository.addAsync(tipType)
    }

    override suspend fun updateAsync(tipType: TipType): Result<Unit> {
        return innerRepository.updateAsync(tipType)
    }

    override suspend fun deleteAsync(tipType: TipType): Result<Unit> {
        return innerRepository.deleteAsync(tipType)
    }
}