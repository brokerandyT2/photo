// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/ITipTypeRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Repository interface for TipType operations
 */
interface ITipTypeRepository {
    suspend fun getByIdAsync(id: Int): Result<TipType?>
    suspend fun getAllAsync(): Result<List<TipType>>
    suspend fun addAsync(tipType: TipType): Result<TipType>
    suspend fun updateAsync(tipType: TipType): Result<Unit>
    suspend fun deleteAsync(tipType: TipType): Result<Unit>
}