//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/UpdateTipCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateTipCommand
import com.x3squaredcircles.pixmap.shared.application.dto.TipDto
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
) : IRequestHandler<UpdateTipCommand, TipDto> {

    override suspend fun handle(request: UpdateTipCommand): TipDto {
        try {
            val tipResult = tipRepository.getByIdAsync(request.id)

            if (!tipResult.isSuccess || tipResult.data == null) {
                mediator.publish(
                    TipValidationErrorEvent(
                        tipId = request.id,
                        validationMessage = "Tip not found"
                    )
                )
                throw IllegalArgumentException("Tip not found")
            }

            val tip = tipResult.data!!

            // Update tip properties
            tip.updateContent(request.title, request.content)

            // Set photography parameters
            tip.updatePhotographySettings(
                request.fstop,
                request.shutterSpeed,
                request.iso
            )

            // Update localization if provided, otherwise keep existing or default
            tip.setLocalization(request.i8n)

            val updateResult = tipRepository.updateAsync(tip)
            if (!updateResult.isSuccess) {
                throw RuntimeException("Failed to update tip: ${updateResult.errorMessage}")
            }

            // Create response DTO
            return TipDto(
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
                            validationMessage = "Duplicate title"
                        )
                    )
                    throw IllegalArgumentException("Duplicate tip title: ${request.title}")
                }
                "INVALID_CONTENT" -> {
                    mediator.publish(
                        TipValidationErrorEvent(
                            tipId = request.id,
                            validationMessage = "Invalid content"
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
                    validationMessage = ex.message ?: "Update operation failed"
                )
            )
            throw RuntimeException("Failed to update tip: ${ex.message}", ex)
        }
    }
}