// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateTipCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateTipCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Handler for CreateTipCommand
 */
class CreateTipCommandHandler(
    private val tipRepository: ITipRepository
) : IRequestHandler<CreateTipCommand, Tip> {

    override suspend fun handle(request: CreateTipCommand): Tip {
        val tip = Tip(
            tipTypeId = request.tipTypeId,
            title = request.title,
            content = request.content
        )

        if (request.fstop != null || request.shutterSpeed != null || request.iso != null) {
            tip.updatePhotographySettings(request.fstop, request.shutterSpeed, request.iso)
        }

        val result = tipRepository.createAsync(tip)

        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            throw RuntimeException("Failed to create tip: ${result}")
        }
    }
}