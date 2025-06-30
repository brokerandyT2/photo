// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateTipTypeCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateTipTypeCommand
import com.x3squaredcircles.pixmap.shared.application.dto.TipTypeDto
import com.x3squaredcircles.pixmap.shared.application.events.TipTypeErrorEvent
import com.x3squaredcircles.pixmap.shared.application.events.TipTypeErrorType
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType
import com.x3squaredcircles.pixmap.shared.domain.exceptions.TipTypeDomainException

/**
 * Handler for CreateTipTypeCommand
 */
class CreateTipTypeCommandHandler(
    private val tipTypeRepository: ITipTypeRepository,
    private val mediator: IMediator
) : IRequestHandler<CreateTipTypeCommand, TipTypeDto> {

    override suspend fun handle(request: CreateTipTypeCommand): TipTypeDto {
        try {
            val tipType = TipType(request.name)
            tipType.setLocalization(request.i8n)

            val result = tipTypeRepository.addAsync(tipType)

            if (!result.isSuccess) {
                mediator.publish(
                    TipTypeErrorEvent(
                        name = request.name,
                        tipTypeId = null,
                        errorType = TipTypeErrorType.DATABASE_ERROR,
                        message = result.exceptionOrNull()?.message ?: "Create failed"
                    )
                )
                throw RuntimeException("Failed to create tip type: ${result.exceptionOrNull()?.message}")
            }

            val createdTipType = result.getOrThrow()

            return TipTypeDto(
                id = createdTipType.id,
                name = createdTipType.name,
                i8n = createdTipType.i8n
            )
        } catch (ex: TipTypeDomainException) {
            when (ex.code) {
                "DUPLICATE_NAME" -> {
                    mediator.publish(
                        TipTypeErrorEvent(
                            name = request.name,
                            tipTypeId = null,
                            errorType = TipTypeErrorType.DUPLICATE_NAME,
                            message = ex.message ?: "Duplicate name"
                        )
                    )
                    throw IllegalArgumentException("Duplicate tip type name: ${request.name}")
                }
                "INVALID_NAME" -> {
                    mediator.publish(
                        TipTypeErrorEvent(
                            name = request.name,
                            tipTypeId = null,
                            errorType = TipTypeErrorType.INVALID_NAME,
                            message = ex.message ?: "Invalid name"
                        )
                    )
                    throw IllegalArgumentException("Invalid tip type name")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                TipTypeErrorEvent(
                    name = request.name,
                    tipTypeId = null,
                    errorType = TipTypeErrorType.DATABASE_ERROR,
                    message = ex.message ?: "Create operation failed"
                )
            )
            throw RuntimeException("Failed to create tip type: ${ex.message}", ex)
        }
    }
}