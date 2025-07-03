//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/queries/GetSettingByKeyQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.queries

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.application.queries.GetSettingByKeyQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Handler for GetSettingByKeyQuery
 */
class GetSettingByKeyQueryHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<GetSettingByKeyQuery, Setting?> {

    override suspend fun handle(request: GetSettingByKeyQuery): Setting? {
        val result = settingRepository.getByKeyAsync(request.key)

        if (!result.isSuccess) {
            throw RuntimeException("Failed to retrieve setting: ${result.errorMessage}")
        }

        val setting = result.data

        return setting
    }
}