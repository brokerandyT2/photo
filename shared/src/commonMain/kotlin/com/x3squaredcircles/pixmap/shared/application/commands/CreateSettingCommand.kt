// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/commands/CreateSettingCommand.kt
package com.x3squaredcircles.pixmap.shared.application.commands

import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequest
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Command to create a new setting
 */
data class CreateSettingCommand(
    val key: String,
    val value: String,
    val description: String = ""
) : IRequest<Setting>