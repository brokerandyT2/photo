// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetRandomTipByTypeQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetRandomTipByTypeQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Handler for GetRandomTipByTypeQuery
 */
class GetRandomTipByTypeQueryHandler(
    private val tipRepository: ITipRepository
) : IRequestHandler<GetRandomTipByTypeQuery, Tip?> {

    override suspend fun handle(request: GetRandomTipByTypeQuery): Tip? {
        val result = tipRepository.getRandomByTypeAsync(request.tipTypeId)

        return if (result.isSuccess) {
            result.data
        } else {
            throw RuntimeException("Failed to get random tip: ${result}")
        }
    }
}