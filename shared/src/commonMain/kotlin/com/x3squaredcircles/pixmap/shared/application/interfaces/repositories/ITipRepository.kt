// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/interfaces/repositories/ITipRepository.kt
package com.x3squaredcircles.pixmap.shared.application.interfaces.repositories

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Repository interface for Tip operations
 */
interface ITipRepository {
    suspend fun getByIdAsync(id: Int): Result<Tip?>
    suspend fun getAllAsync(): Result<List<Tip>>
    suspend fun getByTypeAsync(tipTypeId: Int): Result<List<Tip>>
    suspend fun createAsync(tip: Tip): Result<Tip>
    suspend fun updateAsync(tip: Tip): Result<Tip>
    suspend fun deleteAsync(id: Int): Result<Boolean>
    suspend fun getRandomByTypeAsync(tipTypeId: Int): Result<Tip?>
}