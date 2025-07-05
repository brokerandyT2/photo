// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/queries/GetTipsByTypeQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Handler for GetTipsByTypeQuery
 */
class GetTipsByTypeQueryHandler(
    private val tipRepository: ITipRepository
) : IRequestHandler<GetTipsByTypeQuery, List<Tip>> {

    override suspend fun handle(request: GetTipsByTypeQuery): List<Tip> {
        val result = tipRepository.getByTypeAsync(request.tipTypeId)

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve tips for type ${request.tipTypeId}: ${result.errorMessage}")
        }

        return result.data ?: emptyList()
    }
}