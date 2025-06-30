// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/UpdateTipCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateTipCommand
import com.x3squaredcircles.pixmap.shared.application.dto.UpdateTipCommandResponse
import com.x3squaredcircles.pixmap.shared.application.events.TipValidationErrorEvent
import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.domain.exceptions.TipDomainException

/**
 * Handler for UpdateTipCommand
 */
class UpdateTipCommandHandler(
    private val tipRepository: ITipRepository,
    private val mediator: IMediator
) : IRequestHandler<UpdateTipCommand, UpdateTipCommandResponse> {

    override suspend fun handle(request: UpdateTipCommand): UpdateTipCommandResponse {
        try {
            val tipResult = tipRepository.getByIdAsync(request.id)

            if (!tipResult.isSuccess || tipResult.getOrNull() == null) {
                mediator.publish(
                    TipValidationErrorEvent(
                        tipId = request.id,
                        tipTypeId = request.tipTypeId,
                        errors = listOf("Not found error: Tip not found"),
                        source = "UpdateTipCommandHandler"
                    )
                )
                throw IllegalArgumentException("Tip not found")
            }

            val tip = tipResult.getOrThrow()

            // Update tip properties
            tip.updateContent(request.title, request.content)

            // Set photography parameters
            tip.updatePhotographySettings(
                request.fstop ?: "",
                request.shutterSpeed ?: "",
                request.iso ?: ""
            )

            // Update localization if provided, otherwise keep existing or default
            tip.setLocalization(request.i8n ?: "en-US")

            val updateResult = tipRepository.updateAsync(tip)
            if (!updateResult.isSuccess) {
                throw RuntimeException("Failed to update tip: ${updateResult.exceptionOrNull()?.message}")
            }

            // Create response with the correct ID
            return UpdateTipCommandResponse(
                id = tip.id,
                tipTypeId = tip.tipTypeId,
                title = tip.title,
                content = tip.content,
                fstop = tip.fstop,
                shutterSpeed = tip.shutterSpeed,
                iso = tip.iso,
                i8n = tip.i8n
            )
        } catch (ex: TipDomainException) {
            when (ex.code) {
                "DUPLICATE_TITLE" -> {
                    mediator.publish(
                        TipValidationErrorEvent(
                            tipId = request.id,
                            tipTypeId = request.tipTypeId,
                            errors = listOf("Title validation error: Duplicate title"),
                            source = "UpdateTipCommandHandler"
                        )
                    )
                    throw IllegalArgumentException("Duplicate tip title: ${request.title}")
                }
                "INVALID_CONTENT" -> {
                    mediator.publish(
                        TipValidationErrorEvent(
                            tipId = request.id,
                            tipTypeId = request.tipTypeId,
                            errors = listOf("Content validation error: Invalid content"),
                            source = "UpdateTipCommandHandler"
                        )
                    )
                    throw IllegalArgumentException("Invalid content")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                TipValidationErrorEvent(
                    tipId = request.id,
                    tipTypeId = request.tipTypeId,
                    errors = listOf("Domain error: ${ex.message}"),
                    source = "UpdateTipCommandHandler"
                )
            )
            throw RuntimeException("Failed to update tip: ${ex.message}", ex)
        }
    }
}