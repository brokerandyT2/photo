// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/GetSettingByKeyQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import kotlinx.coroutines.cancellation.CancellationException
import kotlinx.datetime.Instant

/**
 * Represents a query to retrieve a specific setting by its key.
 *
 * This query is used to fetch the value of a setting identified by a unique key. The result
 * contains the setting's value if the key exists, or an appropriate error if it does not.
 */
data class GetSettingByKeyQuery(
    val key: String
)

/**
 * Represents the response for a query that retrieves a setting by its key.
 *
 * This class encapsulates the details of a setting, including its identifier, key, value,
 * description, and the timestamp indicating when the setting was last updated.
 */
data class GetSettingByKeyQueryResponse(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
)

/**
 * Handles the query to retrieve a setting by its key.
 *
 * This handler processes a [GetSettingByKeyQuery] and retrieves the corresponding
 * setting from the data store using the provided key. If the setting is found, it returns a successful result
 * containing the setting details. If the setting is not found or an error occurs, it returns a failure result with
 * an appropriate error message.
 */
class GetSettingByKeyQueryHandler(
    private val unitOfWork: IUnitOfWork
) : IRequestHandler<GetSettingByKeyQuery, GetSettingByKeyQueryResponse> {

    /**
     * Handles the query to retrieve a setting by its key.
     *
     * @param request The query containing the key of the setting to retrieve.
     * @return A [Result] containing a [GetSettingByKeyQueryResponse] if the setting is found;
     * otherwise, a failure result with an appropriate error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: GetSettingByKeyQuery): Result<GetSettingByKeyQueryResponse> {
        return try {
            val result = unitOfWork.settings.getByKeyAsync(request.key)

            if (!result.isSuccess || result.data == null) {
                return Result.failure(
                    result.errorMessage ?: AppResources.getSettingErrorKeyNotFoundSpecific(request.key)
                )
            }

            val setting = result.data

            val response = GetSettingByKeyQueryResponse(
                id = setting.id,
                key = setting.key,
                value = setting.value,
                description = setting.description,
                timestamp = setting.timestamp
            )

            Result.success(response)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Result.failure(AppResources.getSettingErrorKeyNotFoundSpecific(request.key))
        }
    }
}