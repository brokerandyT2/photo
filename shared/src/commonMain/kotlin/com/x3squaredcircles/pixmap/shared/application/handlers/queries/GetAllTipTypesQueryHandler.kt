// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetAllTipTypesQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllTipTypesQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Handler for GetAllTipTypesQuery
 */
class GetAllTipTypesQueryHandler(
    private val tipTypeRepository: ITipTypeRepository
) : IRequestHandler<GetAllTipTypesQuery, List<TipType>> {

    override suspend fun handle(request: GetAllTipTypesQuery): List<TipType> {
        val result = tipTypeRepository.getAllAsync()

        return if (result.isSuccess) {
            result.data ?: emptyList()
        } else {
            throw RuntimeException("Failed to get tip types: ${result}")
        }
    }
}