// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.common.Result
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException
import kotlinx.datetime.Instant

/**
 * Response for CreateSettingCommand
 */
data class CreateSettingCommandResponse(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
)

/**
 * Make CreateSettingCommand implement IRequest
 */
interface CreateSettingCommandRequest : IRequest<CreateSettingCommandResponse>

/**
 * Handler for CreateSettingCommand
 */
class CreateSettingCommandHandler(
    private val settingRepository: ISettingRepository,
    private val mediator: IMediator
) : IRequestHandler<CreateSettingCommandRequest, CreateSettingCommandResponse> {

    override suspend fun handle(request: CreateSettingCommandRequest): CreateSettingCommandResponse {
        val createRequest = request as CreateSettingCommand

        try {
            val existingSettingResult = settingRepository.getByKeyAsync(createRequest.key)

            if (existingSettingResult.isSuccess && existingSettingResult.data != null) {
                mediator.publish(
                    SettingErrorEvent(
                        settingKey = createRequest.key,
                        errorType = SettingErrorType.DUPLICATE_KEY,
                        additionalContext = "Setting with this key already exists"
                    )
                )
                throw IllegalArgumentException("Setting with key '${createRequest.key}' already exists")
            }

            val setting = Setting(
                key = createRequest.key,
                value = createRequest.value,
                description = createRequest.description
            )

            val result = settingRepository.createAsync(setting)

            if (!result.isSuccess) {
                mediator.publish(
                    SettingErrorEvent(
                        settingKey = createRequest.key,
                        errorType = SettingErrorType.DATABASE_ERROR,
                        additionalContext = result.data?.description ?: "Create failed"
                    )
                )
                throw RuntimeException("Failed to create setting: ${result.data?.description}")
            }

            val createdSetting = result.data

            return CreateSettingCommandResponse(
                id = setting.id ,
                key = setting.key,
                value = setting.value,
                description = setting.description,
                timestamp = setting.timestamp
            )
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "DUPLICATE_KEY" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = createRequest.key,
                            errorType = SettingErrorType.DUPLICATE_KEY,
                            additionalContext = ex.message ?: "Duplicate key"
                        )
                    )
                    throw IllegalArgumentException("Setting with key '${createRequest.key}' already exists")
                }
                "INVALID_VALUE" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            settingKey = createRequest.key,
                            errorType = SettingErrorType.INVALID_VALUE,
                            additionalContext = ex.message ?: "Invalid value"
                        )
                    )
                    throw IllegalArgumentException("Invalid value provided")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                SettingErrorEvent(
                    settingKey = createRequest.key,
                    errorType = SettingErrorType.DATABASE_ERROR,
                    additionalContext = ex.message ?: "Create operation failed"
                )
            )
            throw RuntimeException("Failed to create setting: ${ex.message}", ex)
        }
    }
}