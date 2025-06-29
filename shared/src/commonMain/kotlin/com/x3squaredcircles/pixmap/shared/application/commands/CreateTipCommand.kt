// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/CreateTipCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.Tip

/**
 * Command to create a new tip
 */
data class CreateTipCommand(
    val tipTypeId: Int,
    val title: String,
    val content: String,
    val fstop: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null
) : IRequest<Tip>