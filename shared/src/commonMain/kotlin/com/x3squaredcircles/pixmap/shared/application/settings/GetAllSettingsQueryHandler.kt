// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/GetAllSettingsQueryHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import kotlinx.coroutines.cancellation.CancellationException
import kotlinx.datetime.Instant

/**
 * Represents a query to retrieve all settings.
 *
 * This query is used to request a list of all available settings. The result contains a
 * collection of [GetAllSettingsQueryResponse] objects wrapped in a [Result].
 */
class GetAllSettingsQuery

/**
 * Represents the response for a query that retrieves all settings.
 *
 * This class encapsulates the details of a single setting, including its identifier, key,
 * value, description, and the timestamp indicating when the setting was last updated.
 */
data class GetAllSettingsQueryResponse(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
)

/**
 * Handles the query to retrieve all settings from the data source.
 *
 * This handler processes a [GetAllSettingsQuery] and returns a result containing a
 * list of [GetAllSettingsQueryResponse] objects. If the retrieval operation fails, the result will
 * indicate failure with an appropriate error message.
 */
class GetAllSettingsQueryHandler(
    private val unitOfWork: IUnitOfWork
) : IRequestHandler<GetAllSettingsQuery, List<GetAllSettingsQueryResponse>> {

    /**
     * Handles the query to retrieve all settings from the data source.
     *
     * This method retrieves all settings from the underlying data source and maps them to
     * [GetAllSettingsQueryResponse] objects. If the retrieval fails or no data is available, the
     * method returns a failure result with an appropriate error message.
     *
     * @param request The query request containing the parameters for retrieving settings.
     * @return A [Result] containing a list of [GetAllSettingsQueryResponse] objects if the
     * operation is successful; otherwise, a failure result with an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: GetAllSettingsQuery): Result<List<GetAllSettingsQueryResponse>> {
        return try {
            val result = unitOfWork.settings.getAllAsync()

            if (!result.isSuccess || result.data == null) {
                return Result.failure(
                    result.errorMessage ?: AppResources.settingErrorRetrieveFailed
                )
            }

            val settings = result.data

            val response = settings.map { setting ->
                GetAllSettingsQueryResponse(
                    id = setting.id,
                    key = setting.key,
                    value = setting.value,
                    description = setting.description,
                    timestamp = setting.timestamp
                )
            }

            Result.success(response)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Result.failure(AppResources.settingErrorRetrieveFailed)
        }
    }
}