// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/TipCommands.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.dto.TipDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest


/**
 * Command to delete a tip by ID
 */
data class DeleteTipCommand(
    val id: Int
) : IRequest<Boolean>