// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetSettingByKeyQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.GetSettingByKeyQueryResponse
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetSettingByKeyQuery

/**
 * Handler for GetSettingByKeyQuery
 */
class GetSettingByKeyQueryHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<GetSettingByKeyQuery, GetSettingByKeyQueryResponse> {

    override suspend fun handle(request: GetSettingByKeyQuery): GetSettingByKeyQueryResponse {
        val result = settingRepository.getByKeyAsync(request.key)

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve setting: ${result.exceptionOrNull()?.message}")
        }

        val setting = result.getOrNull()
            ?: throw IllegalArgumentException("Setting with key '${request.key}' not found")

        return GetSettingByKeyQueryResponse(
            id = setting.id,
            key = setting.key,
            value = setting.value,
            description = setting.description,
            timestamp = setting.timestamp
        )
    }
}