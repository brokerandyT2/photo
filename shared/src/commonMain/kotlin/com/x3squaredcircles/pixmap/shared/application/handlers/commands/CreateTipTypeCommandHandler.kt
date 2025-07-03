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
) : IRequestHandler<CreateTipTypeCommand, TipType> {

    override suspend fun handle(request: CreateTipTypeCommand): TipType {
        try {
            val tipType = TipType.create(request.name, request.i8n)

            val result = tipTypeRepository.addAsync(tipType)

            if (!result.isSuccess) {
                mediator.publish(
                    TipTypeErrorEvent(
                        tipTypeName = request.name,
                        errorType = TipTypeErrorType.DATABASE_ERROR,
                        additionalContext = "Create failed"
                    )
                )
                throw RuntimeException("Failed to create tip type: ${result.data?.toString()}")
            }

            val createdTipType = result.data
            if (createdTipType == null) {
                mediator.publish(
                    TipTypeErrorEvent(
                        tipTypeName = request.name,
                        errorType = TipTypeErrorType.DATABASE_ERROR,
                        additionalContext = "Create failed - data is null"
                    )
                )
                throw RuntimeException("Failed to create tip type: data is null")
            }

            return createdTipType
        } catch (ex: TipTypeDomainException) {
            when (ex.code) {
                "DUPLICATE_NAME" -> {
                    mediator.publish(
                        TipTypeErrorEvent(
                            tipTypeName = request.name,
                            errorType = TipTypeErrorType.ALREADY_EXISTS,
                            additionalContext = "Duplicate name"
                        )
                    )
                    throw IllegalArgumentException("Duplicate tip type name: ${request.name}")
                }
                "INVALID_NAME" -> {
                    mediator.publish(
                        TipTypeErrorEvent(
                            tipTypeName = request.name,
                            errorType = TipTypeErrorType.DATABASE_ERROR,
                            additionalContext = "Invalid name"
                        )
                    )
                    throw IllegalArgumentException("Invalid tip type name")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                TipTypeErrorEvent(
                    tipTypeName = request.name,
                    errorType = TipTypeErrorType.DATABASE_ERROR,
                    additionalContext = "Create operation failed"
                )
            )
            throw RuntimeException("Failed to create tip type: ${ex.message}", ex)
        }
    }
}