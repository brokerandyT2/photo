// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.dto.CreateSettingCommandResponse
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException

/**
 * Handler for CreateSettingCommand
 */
class CreateSettingCommandHandler(
    private val settingRepository: ISettingRepository,
    private val mediator: IMediator
) : IRequestHandler<CreateSettingCommand, CreateSettingCommandResponse> {

    override suspend fun handle(request: CreateSettingCommand): CreateSettingCommandResponse {
        try {
            val existingSettingResult = settingRepository.getByKeyAsync(request.key)

            if (existingSettingResult.isSuccess && existingSettingResult.getOrNull() != null) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.DUPLICATE_KEY,
                        message = "Setting with this key already exists"
                    )
                )
                throw IllegalArgumentException("Setting with key '${request.key}' already exists")
            }

            val setting = Setting(
                key = request.key,
                value = request.value,
                description = request.description
            )

            val result = settingRepository.createAsync(setting)

            if (!result.isSuccess) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.DATABASE_ERROR,
                        message = result.exceptionOrNull()?.message ?: "Create failed"
                    )
                )
                throw RuntimeException("Failed to create setting: ${result.exceptionOrNull()?.message}")
            }

            val createdSetting = result.getOrThrow()

            return CreateSettingCommandResponse(
                id = createdSetting.id,
                key = createdSetting.key,
                value = createdSetting.value,
                description = createdSetting.description,
                timestamp = createdSetting.timestamp
            )
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "DUPLICATE_KEY" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.DUPLICATE_KEY,
                            message = ex.message ?: "Duplicate key"
                        )
                    )
                    throw IllegalArgumentException("Setting with key '${request.key}' already exists")
                }
                "INVALID_VALUE" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.INVALID_VALUE,
                            message = ex.message ?: "Invalid value"
                        )
                    )
                    throw IllegalArgumentException("Invalid value provided")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    key = request.key,
                    errorType = SettingErrorType.DATABASE_ERROR,
                    message = ex.message ?: "Create operation failed"
                )
            )
            throw RuntimeException("Failed to create setting: ${ex.message}", ex)
        }
    }
}