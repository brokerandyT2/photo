// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/CreateTipTypeCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.TipType

/**
 * Command to create a new tip type
 */
data class CreateTipTypeCommand(
    val name: String,
    val i8n: String = "en-US"
) : IRequest<TipType>