// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/UpdateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException
import kotlinx.coroutines.CancellationException

/**
 * Handles the updating of an existing setting by processing an [UpdateSettingCommand] request.
 *
 * This handler retrieves the setting by its key, updates its value, and persists the changes
 * to the data store. If the setting is not found or the update operation fails, an appropriate error is thrown.
 */
class UpdateSettingCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<UpdateSettingCommand, Setting> {

    /**
     * Handles the update of an existing setting based on the provided command.
     *
     * This method retrieves the setting by its key, updates its value, and persists the changes.
     * If the update operation fails, an exception is thrown.
     *
     * @param request The command containing the key of the setting to update and the new value.
     * @return The updated [Setting] entity.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: UpdateSettingCommand): Setting {
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
                throw IllegalArgumentException("Setting with key '${request.key}' not found")
            }

            val setting = settingResult.data!!
            setting.updateValue(request.value)

            val updateResult = unitOfWork.settings.updateAsync(setting)

            if (!updateResult.isSuccess || updateResult.data == null) {
                mediator.publish(
                    SettingErrorEvent(
                        settingKey = request.key,
                        errorType = SettingErrorType.DATABASE_ERROR,
                        additionalContext = updateResult.errorMessage ?: "Update operation failed"
                    )
                )
                throw RuntimeException("Failed to update setting: ${updateResult.errorMessage}")
            }

            updateResult.data!!
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "READ_ONLY_SETTING" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = request.key,
                            errorType = SettingErrorType.READ_ONLY_SETTING,
                            additionalContext = ex.message ?: "Cannot update read-only setting"
                        )
                    )
                    throw IllegalStateException("Cannot update read-only setting")
                }
                "INVALID_VALUE" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = request.key,
                            errorType = SettingErrorType.INVALID_VALUE,
                            additionalContext = ex.message ?: "Invalid value provided"
                        )
                    )
                    throw IllegalArgumentException("Invalid value provided")
                }
                else -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = request.key,
                            errorType = SettingErrorType.DATABASE_ERROR,
                            additionalContext = ex.message ?: "Database error"
                        )
                    )
                    throw RuntimeException("Database error: ${ex.message}")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    settingKey = request.key,
                    errorType = SettingErrorType.DATABASE_ERROR,
                    additionalContext = ex.message ?: "Update operation failed"
                )
            )
            throw RuntimeException("Failed to update setting: ${ex.message}", ex)
        }
    }
}