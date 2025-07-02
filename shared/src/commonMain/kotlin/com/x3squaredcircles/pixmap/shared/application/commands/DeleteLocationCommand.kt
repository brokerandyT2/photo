//shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/DeleteLocationCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.application.common.models.Result

/**
 * Command to delete a location by its identifier.
 */
data class DeleteLocationCommand(
    val id: Int
) : IRequest<Result<Boolean>>