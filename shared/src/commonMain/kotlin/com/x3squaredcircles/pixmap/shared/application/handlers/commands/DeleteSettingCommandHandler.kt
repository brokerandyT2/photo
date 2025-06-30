// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/DeleteSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.DeleteSettingCommand
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException

/**
 * Handler for DeleteSettingCommand
 */
class DeleteSettingCommandHandler(
    private val settingRepository: ISettingRepository,
    private val mediator: IMediator
) : IRequestHandler<DeleteSettingCommand, Boolean> {

    override suspend fun handle(request: DeleteSettingCommand): Boolean {
        try {
            val settingResult = settingRepository.getByKeyAsync(request.key)

            if (!settingResult.isSuccess || settingResult.getOrNull() == null) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.KEY_NOT_FOUND,
                        message = "Setting not found"
                    )
                )
                throw IllegalArgumentException("Setting with key '${request.key}' not found")
            }

            val result = settingRepository.deleteAsync(request.key)

            if (!result.isSuccess) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.DATABASE_ERROR,
                        message = result.exceptionOrNull()?.message ?: "Delete failed"
                    )
                )
                throw RuntimeException("Failed to delete setting: ${result.exceptionOrNull()?.message}")
            }

            return true
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "READ_ONLY_SETTING" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.READ_ONLY_SETTING,
                            message = ex.message ?: "Cannot delete read-only setting"
                        )
                    )
                    throw IllegalStateException("Cannot delete read-only setting")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    key = request.key,
                    errorType = SettingErrorType.DATABASE_ERROR,
                    message = ex.message ?: "Delete operation failed"
                )
            )
            throw RuntimeException("Failed to delete setting: ${ex.message}", ex)
        }
    }
}