// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/UpdateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.UpdateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Handler for UpdateSettingCommand
 */
class UpdateSettingCommandHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<UpdateSettingCommand, Setting> {

    override suspend fun handle(request: UpdateSettingCommand): Setting {
        val existingResult = settingRepository.getByKeyAsync(request.key)

        if (!existingResult.isSuccess) {
            throw RuntimeException("Failed to get setting: ${existingResult}")
        }

        val setting = existingResult.getOrNull()
            ?: throw IllegalArgumentException("Setting with key '${request.key}' not found")

        setting.updateValue(request.value)

        val result = settingRepository.updateAsync(setting)

        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            throw RuntimeException("Failed to update setting: ${result}")
        }
    }
}