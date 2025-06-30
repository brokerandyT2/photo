// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetAllSettingsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.dto.GetAllSettingsQueryResponse
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllSettingsQuery

/**
 * Handler for GetAllSettingsQuery
 */
class GetAllSettingsQueryHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<GetAllSettingsQuery, List<GetAllSettingsQueryResponse>> {

    override suspend fun handle(request: GetAllSettingsQuery): List<GetAllSettingsQueryResponse> {
        val result = settingRepository.getAllAsync()

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve settings: ${result.exceptionOrNull()?.message}")
        }

        val settings = result.getOrNull() ?: emptyList()

        return settings.map { setting ->
            GetAllSettingsQueryResponse(
                id = setting.id,
                key = setting.key,
                value = setting.value,
                description = setting.description,
                timestamp = setting.timestamp
            )
        }
    }
}