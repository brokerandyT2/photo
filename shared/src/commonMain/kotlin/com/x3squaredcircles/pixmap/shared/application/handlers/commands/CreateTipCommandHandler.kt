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
) : IRequestHandler<CreateTipCommand, List<TipDto>> {

    override suspend fun handle(request: CreateTipCommand): List<TipDto> {
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

            if (!request.i8n.isNullOrBlank()) {
                tip.setLocalization(request.i8n)
            }

            val result = tipRepository.createAsync(tip)

            if (!result.isSuccess) {
                mediator.publish(
                    TipValidationErrorEvent(
                        tipId = null,
                        tipTypeId = request.tipTypeId,
                        errors = listOf("Database error: ${result.exceptionOrNull()?.message}"),
                        source = "CreateTipCommandHandler"
                    )
                )
                throw RuntimeException("Failed to create tip: ${result.exceptionOrNull()?.message}")
            }

            val createdTip = result.getOrThrow()

            val tipDto = TipDto(
                id = createdTip.id,
                tipTypeId = createdTip.tipTypeId,
                title = createdTip.title,
                content = createdTip.content,
                fstop = createdTip.fstop,
                shutterSpeed = createdTip.shutterSpeed,
                iso = createdTip.iso,
                i8n = createdTip.i8n
            )

            return listOf(tipDto)
        } catch (ex: TipDomainException) {
            when (ex.code) {
                "DUPLICATE_TITLE" -> {
                    mediator.publish(
                        TipValidationErrorEvent(
                            tipId = null,
                            tipTypeId = request.tipTypeId,
                            errors = listOf("Title validation error: Duplicate title"),
                            source = "CreateTipCommandHandler"
                        )
                    )
                    throw IllegalArgumentException("Duplicate tip title: ${request.title}")
                }
                "INVALID_TIP_TYPE" -> {
                    mediator.publish(
                        TipValidationErrorEvent(
                            tipId = null,
                            tipTypeId = request.tipTypeId,
                            errors = listOf("TipTypeId validation error: Invalid tip type"),
                            source = "CreateTipCommandHandler"
                        )
                    )
                    throw IllegalArgumentException("Invalid tip type")
                }
                else -> throw ex
            }
        } catch (ex: Exception) {
            mediator.publish(
                TipValidationErrorEvent(
                    tipId = null,
                    tipTypeId = request.tipTypeId,
                    errors = listOf("Domain error: ${ex.message}"),
                    source = "CreateTipCommandHandler"
                )
            )
            throw RuntimeException("Failed to create tip: ${ex.message}", ex)
        }
    }
}