//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetAllSettingsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Query to get all settings
 */
class GetAllSettingsQuery : IRequest<List<Setting>>

/**
 * Handler for GetAllSettingsQuery
 */
class GetAllSettingsQueryHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<GetAllSettingsQuery, List<Setting>> {

    override suspend fun handle(request: GetAllSettingsQuery): List<Setting> {
        val result = settingRepository.getAllAsync()

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve settings: ${result.errorMessage}")
        }

        val settings = result.data ?: emptyList()

        return settings
    }
}