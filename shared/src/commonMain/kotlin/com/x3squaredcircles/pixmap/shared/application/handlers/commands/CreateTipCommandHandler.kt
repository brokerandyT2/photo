// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateTipCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateTipCommand
import com.x3squaredcircles.pixmap.shared.application.dto.TipDto
import com.x3squaredcircles.pixmap.shared.application.events.TipValidationErrorEvent
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip
import com.x3squaredcircles.pixmap.shared.domain.exceptions.TipDomainException

/**
 * Handler for CreateTipCommand
 */
class CreateTipCommandHandler(
    private val tipRepository: ITipRepository,
    private val mediator: IMediator
) : IRequestHandler<CreateTipCommand, Tip> {

    override suspend fun handle(request: CreateTipCommand): Tip {
        try {
            val tip = Tip(
                tipTypeId = request.tipTypeId,
                title = request.title,
                content = request.content
            )

            // Set photography parameters if provided
            if (!request.fstop.isNullOrBlank() || !request.shutterSpeed.isNullOrBlank() || !request.iso.isNullOrBlank()) {
                tip.updatePhotographySettings(
                    request.fstop ?: "",
                    request.shutterSpeed ?: "",
                    request.iso ?: ""
                )
            }

            val result = tipRepository.createAsync(tip)

            if (!result.isSuccess) {
                val errorEvent = TipValidationErrorEvent(
                    tipId = 0,
                    validationMessage = "Database error: ${result.data?.toString()}"
                )
                mediator.publish(errorEvent)
                throw RuntimeException("Failed to create tip: ${result.data?.toString()}")
            }

            val createdTip = result.data
            if (createdTip == null) {
                val errorEvent = TipValidationErrorEvent(
                    tipId = 0,
                    validationMessage = "Failed to create tip: data is null"
                )
                mediator.publish(errorEvent)
                throw RuntimeException("Failed to create tip: data is null")
            }

            return createdTip
        } catch (ex: TipDomainException) {
            when (ex.code) {
                "DUPLICATE_TITLE" -> {
                    val errorEvent = TipValidationErrorEvent(
                        tipId = 0,
                        validationMessage = "Title validation error: Duplicate title"
                    )
                    mediator.publish(errorEvent)
                    throw IllegalArgumentException("Duplicate tip title: ${request.title}")
                }
                "INVALID_TIP_TYPE" -> {
                    val errorEvent = TipValidationErrorEvent(
                        tipId = 0,
                        validationMessage = "TipTypeId validation error: Invalid tip type"
                    )
                    mediator.publish(errorEvent)
                    throw IllegalArgumentException("Invalid tip type")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            val errorEvent = TipValidationErrorEvent(
                tipId = 0,
                validationMessage = "Domain error: ${ex.message}"
            )
            mediator.publish(errorEvent)
            throw RuntimeException("Failed to create tip: ${ex.message}", ex)
        }
    }
}