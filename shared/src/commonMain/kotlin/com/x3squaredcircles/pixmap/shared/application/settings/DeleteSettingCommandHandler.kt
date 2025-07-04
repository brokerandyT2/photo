// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/DeleteSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException
import kotlinx.coroutines.CancellationException

/**
 * Represents a command to delete a setting identified by its key.
 *
 * This command is used to request the deletion of a specific setting in the system. The result
 * of the operation indicates whether the deletion was successful.
 */
data class DeleteSettingCommand(
    val key: String
) : IRequest<Result<Boolean>>

/**
 * Handles the deletion of a setting by its key.
 *
 * This handler processes a [DeleteSettingCommand] and attempts to delete the corresponding
 * setting from the data store. If the setting is not found or the deletion fails, it returns a failure result
 * with an appropriate error message.
 */
class DeleteSettingCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<DeleteSettingCommand, Result<Boolean>> {

    /**
     * Handles the deletion of a setting by its key.
     *
     * This method attempts to find the setting by its key and delete it from the data store.
     * If the setting is not found or the deletion operation fails, it returns a failure result.
     *
     * @param request The command containing the key of the setting to delete.
     * @return A [Result] indicating whether the deletion was successful.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: DeleteSettingCommand): Result<Boolean> {
        return try {
            val settingResult = unitOfWork.settings.getByKeyAsync(request.key)

            if (!settingResult.isSuccess || settingResult.data == null) {
                mediator.publish(
                    SettingErrorEvent(
                        settingKey = request.key,
                        errorType = SettingErrorType.KEY_NOT_FOUND,
                        additionalContext = "Setting with key '${request.key}' not found"
                    )
                )
                return Result.failure("Setting with key '${request.key}' not found")
            }

            val deleteResult = unitOfWork.settings.deleteAsync(settingResult.data!!.key)

            if (!deleteResult.isSuccess) {
                mediator.publish(
                    SettingErrorEvent(
                        settingKey = request.key,
                        errorType = SettingErrorType.DATABASE_ERROR,
                        additionalContext = deleteResult.errorMessage ?: "Delete operation failed"
                    )
                )
                return Result.failure(deleteResult.errorMessage ?: "Delete operation failed")
            }

            Result.success(true)
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "READ_ONLY_SETTING" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = request.key,
                            errorType = SettingErrorType.READ_ONLY_SETTING,
                            additionalContext = ex.message ?: "Cannot delete read-only setting"
                        )
                    )
                    Result.failure("Cannot delete read-only setting")
                }
                "SETTING_NOT_FOUND" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = request.key,
                            errorType = SettingErrorType.KEY_NOT_FOUND,
                            additionalContext = ex.message ?: "Setting not found"
                        )
                    )
                    Result.failure("Setting with key '${request.key}' not found")
                }
                else -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = request.key,
                            errorType = SettingErrorType.DATABASE_ERROR,
                            additionalContext = ex.message ?: "Database error"
                        )
                    )
                    Result.failure(ex.message ?: "Database error occurred")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    settingKey = request.key,
                    errorType = SettingErrorType.DATABASE_ERROR,
                    additionalContext = ex.message ?: "Delete operation failed"
                )
            )
            Result.failure(ex.message ?: "Delete operation failed")
        }
    }
}