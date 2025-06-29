// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/UpdateSettingCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Command to update an existing setting
 */
data class UpdateSettingCommand(
    val key: String,
    val value: String
) : IRequest<Setting>