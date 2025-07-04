// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/GetSettingByKeyQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.queries.GetSettingByKeyQuery
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import kotlinx.coroutines.CancellationException

/**
 * Handles the query to retrieve a setting by its key.
 *
 * This handler processes a [GetSettingByKeyQuery] and retrieves the corresponding
 * setting from the data store using the provided key. If the setting is found, it returns the setting.
 * If the setting is not found or an error occurs, it returns null or throws an exception.
 */
class GetSettingByKeyQueryHandler(
    private val unitOfWork: IUnitOfWork
) : IRequestHandler<GetSettingByKeyQuery, Setting?> {

    /**
     * Handles the query to retrieve a setting by its key.
     *
     * @param request The query containing the key of the setting to retrieve.
     * @return A [Setting] if the setting is found; otherwise, null.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: GetSettingByKeyQuery): Setting? {
        return try {
            val result = unitOfWork.settings.getByKeyAsync(request.key)

            if (!result.isSuccess) {
                throw RuntimeException("Failed to retrieve setting: ${result.errorMessage}")
            }

            result.data
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            throw RuntimeException("Failed to retrieve setting: ${ex.message}", ex)
        }
    }
}