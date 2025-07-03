//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/UpdateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException

/**
 * Handler for UpdateSettingCommand
 */
class UpdateSettingCommandHandler(
    private val settingRepository: ISettingRepository,
    private val mediator: IMediator
) : IRequestHandler<UpdateSettingCommand, Setting> {

    override suspend fun handle(request: UpdateSettingCommand): Setting {
        try {
            val settingResult = settingRepository.getByKeyAsync(request.key)

            if (!settingResult.isSuccess || settingResult.data == null) {
                mediator.publish(
                    SettingErrorEvent(
                        request.key,
                        SettingErrorType.KEY_NOT_FOUND,
                        "Setting not found"
                    )
                )
                throw IllegalArgumentException("Setting with key '${request.key}' not found")
            }

            val setting = settingResult.data!!
            setting.updateValue(request.value)

            val updateResult = settingRepository.updateAsync(setting)

            if (!updateResult.isSuccess) {
                mediator.publish(
                    SettingErrorEvent(
                        request.key,
                        SettingErrorType.DATABASE_ERROR,
                        updateResult.errorMessage ?: "Update failed"
                    )
                )
                throw RuntimeException("Failed to update setting: ${updateResult.errorMessage}")
            }

            val updatedSetting = updateResult.data!!

            return updatedSetting
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "READ_ONLY_SETTING" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            request.key,
                            SettingErrorType.READ_ONLY_SETTING,
                            ex.message ?: "Cannot update read-only setting"
                        )
                    )
                    throw IllegalStateException("Cannot update read-only setting")
                }
                "INVALID_VALUE" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            request.key,
                            SettingErrorType.INVALID_VALUE,
                            ex.message ?: "Invalid value provided"
                        )
                    )
                    throw IllegalArgumentException("Invalid value provided")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    request.key,
                    SettingErrorType.DATABASE_ERROR,
                    ex.message ?: "Update operation failed"
                )
            )
            throw RuntimeException("Failed to update setting: ${ex.message}", ex)
        }
    }
}