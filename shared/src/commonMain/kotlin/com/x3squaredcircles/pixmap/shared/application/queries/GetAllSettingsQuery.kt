// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetAllSettingsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetAllSettingsQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Handler for GetAllSettingsQuery
 */
class GetAllSettingsQueryHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<GetAllSettingsQuery, List<Setting>> {

    override suspend fun handle(request: GetAllSettingsQuery): List<Setting> {
        val result = settingRepository.getAllAsync()

        return if (result.isSuccess) {
            result.getOrNull() ?: emptyList()
        } else {
            throw RuntimeException("Failed to get settings: ${result}")
        }
    }
}