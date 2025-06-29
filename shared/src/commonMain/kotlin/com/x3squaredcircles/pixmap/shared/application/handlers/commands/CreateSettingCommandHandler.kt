// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/application/handlers/commands/CreateSettingCommandHandler.kt
package com.x3squaredcircles.pixmap.shared.application.handlers.commands

import com.x3squaredcircles.pixmap.shared.application.commands.CreateSettingCommand
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.application.interfaces.repositories.ISettingRepository
import com.x3squaredcircles.pixmap.shared.domain.entities.Setting

/**
 * Handler for CreateSettingCommand
 */
class CreateSettingCommandHandler(
    private val settingRepository: ISettingRepository
) : IRequestHandler<CreateSettingCommand, Setting> {

    override suspend fun handle(request: CreateSettingCommand): Setting {
        val setting = Setting(
            key = request.key,
            value = request.value,
            description = request.description
        )

        val result = settingRepository.createAsync(setting)

        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            throw RuntimeException("Failed to create setting: ${result}")
        }
    }
}