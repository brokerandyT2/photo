// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateTipTypeCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateTipTypeCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ITipTypeRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Handler for CreateTipTypeCommand
 */
class CreateTipTypeCommandHandler(
    private val tipTypeRepository: ITipTypeRepository
) : IRequestHandler<CreateTipTypeCommand, TipType> {

    override suspend fun handle(request: CreateTipTypeCommand): TipType {
        val tipType = TipType(request.name)

        if (request.i8n != "en-US") {
            tipType.setLocalization(request.i8n)
        }

        val result = tipTypeRepository.addAsync(tipType)

        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            throw RuntimeException("Failed to create tip type: ${result}")
        }
    }
}