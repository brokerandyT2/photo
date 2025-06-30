// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/UpdateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.events.errors.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.errors.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException
import kotlinx.coroutines.cancellation.CancellationException
import kotlinx.datetime.Instant

/**
 * Represents a command to update a setting with a specified key, value, and optional description.
 *
 * This command is used to modify the value of a setting identified by its key. The optional
 * description can provide additional context about the setting being updated.
 */
data class UpdateSettingCommand(
    val key: String,
    val value: String,
    val description: String? = null
)

/**
 * Represents the response returned after updating a setting in the system.
 *
 * This class contains details about the updated setting, including its identifier, key, value,
 * description, and the timestamp of the update. It is typically used to convey the result of an update operation
 * in a settings management context.
 */
data class UpdateSettingCommandResponse(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
)

/**
 * Handles the updating of an existing setting by processing an [UpdateSettingCommand] request.
 *
 * This handler retrieves the setting by its key, updates its value, and persists the changes
 * to the data store. If the setting is not found or the update operation fails, an appropriate error result is
 * returned.
 */
class UpdateSettingCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<UpdateSettingCommand, UpdateSettingCommandResponse> {

    /**
     * Handles the update of an existing setting based on the provided command.
     *
     * This method retrieves the setting by its key, updates its value, and persists the changes.
     * If the update operation fails, the result will indicate failure with the
     * corresponding error message.
     *
     * @param request The command containing the key of the setting to update and the new value.
     * @return A [Result] containing an [UpdateSettingCommandResponse] with details of the updated setting
     * if successful; otherwise, a failure result with an error message.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: UpdateSettingCommand): Result<UpdateSettingCommandResponse> {
        return try {
            val settingResult = unitOfWork.settings.getByKeyAsync(request.key)

            if (!settingResult.isSuccess || settingResult.data == null) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.KeyNotFound,
                        details = null
                    )
                )
                return Result.failure(AppResources.getSettingErrorKeyNotFoundSpecific(request.key))
            }

            val setting = settingResult.data
            setting.updateValue(request.value)

            // Update description if provided
            request.description?.let { setting.updateDescription(it) }

            val updateResult = unitOfWork.settings.updateAsync(setting)

            if (!updateResult.isSuccess || updateResult.data == null) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.DatabaseError,
                        details = updateResult.errorMessage
                    )
                )
                return Result.failure(
                    updateResult.errorMessage ?: AppResources.settingErrorUpdateFailed
                )
            }

            val updatedSetting = updateResult.data

            val response = UpdateSettingCommandResponse(
                id = updatedSetting.id,
                key = updatedSetting.key,
                value = updatedSetting.value,
                description = updatedSetting.description,
                timestamp = updatedSetting.timestamp
            )

            Result.success(response)
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "READ_ONLY_SETTING" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.ReadOnlySetting,
                            details = ex.message
                        )
                    )
                    Result.failure(AppResources.settingErrorCannotUpdateReadOnly)
                }
                "INVALID_VALUE" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.InvalidValue,
                            details = ex.message
                        )
                    )
                    Result.failure(AppResources.settingErrorInvalidValueProvided)
                }
                else -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.DatabaseError,
                            details = ex.message
                        )
                    )
                    Result.failure(AppResources.getSettingErrorUpdateFailedWithException(ex.message ?: "Domain exception"))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    key = request.key,
                    errorType = SettingErrorType.DatabaseError,
                    details = ex.message
                )
            )
            Result.failure(AppResources.getSettingErrorUpdateFailedWithException(ex.message ?: "Unknown error"))
        }
    }
}