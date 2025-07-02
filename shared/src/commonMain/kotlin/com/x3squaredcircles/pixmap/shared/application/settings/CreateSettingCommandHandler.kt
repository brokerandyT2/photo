// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/settings/CreateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.settings

import com.x3squaredcircles.pixmap.shared.application.common.interfaces.IUnitOfWork
import com.x3squaredcircles.pixmap.shared.application.common.models.Result
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.SettingErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.`events/errors`.SettingErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.resources.AppResources
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting
import com.x3squaredcircles.pixmap.shared.domain.exceptions.SettingDomainException
import kotlinx.coroutines.cancellation.CancellationException
import kotlinx.datetime.Instant

/**
 * Represents a command to create a new setting with a specified key, value, and description.
 *
 * This command is used to encapsulate the data required to create a new setting. The [key] property
 * identifies the setting, the [value] property specifies its value, and the [description] provides
 * additional context or details about the setting.
 */
data class CreateSettingCommand(
    val key: String,
    val value: String,
    val description: String = ""
)

/**
 * Represents the response returned after creating a new setting.
 *
 * This class contains details about the created setting, including its unique identifier, key,
 * value, description, and the timestamp of when it was created.
 */
data class CreateSettingCommandResponse(
    val id: Int,
    val key: String,
    val value: String,
    val description: String,
    val timestamp: Instant
)

/**
 * Handles the creation of a new setting by processing a [CreateSettingCommand] request.
 *
 * This handler ensures that a setting with the specified key does not already exist before
 * creating a new one. If a setting with the same key exists, the operation fails with an appropriate error
 * message.
 */
class CreateSettingCommandHandler(
    private val unitOfWork: IUnitOfWork,
    private val mediator: IMediator
) : IRequestHandler<CreateSettingCommand, CreateSettingCommandResponse> {

    /**
     * Handles the creation of a new setting based on the provided command.
     *
     * If a setting with the specified key already exists, the operation will fail and
     * return an error message.
     *
     * @param request The command containing the key, value, and description for the new setting.
     * @return A [Result] containing a [CreateSettingCommandResponse] if the operation succeeds,
     * or a failure result with an error message if the operation fails.
     * @throws CancellationException If the operation is cancelled.
     */
    override suspend fun handle(request: CreateSettingCommand): Result<CreateSettingCommandResponse> {
        return try {
            val existingSettingResult = unitOfWork.settings.getByKeyAsync(request.key)

            if (existingSettingResult.isSuccess && existingSettingResult.data != null) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.DuplicateKey,
                        details = null
                    )
                )
                return Result.failure(AppResources.getSettingErrorKeyAlreadyExists(request.key))
            }

            val setting = Setting(
                key = request.key,
                value = request.value,
                description = request.description
            )

            val result = unitOfWork.settings.createAsync(setting)

            if (!result.isSuccess || result.data == null) {
                mediator.publish(
                    SettingErrorEvent(
                        key = request.key,
                        errorType = SettingErrorType.DatabaseError,
                        details = result.errorMessage
                    )
                )
                return Result.failure(
                    result.errorMessage ?: AppResources.settingErrorCreateFailed
                )
            }

            val createdSetting = result.data

            val response = CreateSettingCommandResponse(
                id = createdSetting.id,
                key = createdSetting.key,
                value = createdSetting.value,
                description = createdSetting.description,
                timestamp = createdSetting.timestamp
            )

            Result.success(response)
        } catch (ex: SettingDomainException) {
            when (ex.code) {
                "DUPLICATE_KEY" -> {
                    mediator.publish(
                        SettingErrorEvent(
                            key = request.key,
                            errorType = SettingErrorType.DuplicateKey,
                            details = null
                        )
                    )
                    Result.failure(AppResources.getSettingErrorKeyAlreadyExists(request.key))
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
                    Result.failure(AppResources.getSettingErrorCreateFailedWithException(ex.message ?: "Domain exception"))
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
            Result.failure(AppResources.getSettingErrorCreateFailedWithException(ex.message ?: "Unknown error"))
        }
    }
}