// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/TipCommands.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.dto.TipDto
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest


/**
 * Command to update an existing tip
 */
data class UpdateTipCommand(
    val id: Int,
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String = "",
    val shutterSpeed: String = "",
    val iso: String = "",
    val i8n: String = "en-US"
) : IRequest<TipDto>

