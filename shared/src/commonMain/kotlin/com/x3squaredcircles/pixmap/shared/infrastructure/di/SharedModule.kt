// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/infrastructure/di/SharedModule.kt
package com.x3squaredcircles.pixmap.shared.infrastructure.di

import com.x3squaredcircles.pixmap.shared.application.handlers.commands.AttachPhotoCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.CreateLocationCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.CreateSettingCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.CreateTipCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.CreateTipTypeCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.DeleteLocationCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.UpdateLocationCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.UpdateSettingCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.UpdateWeatherCommandHandler

import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetAllLocationsQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetAllSettingsQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetAllTipTypesQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetLocationByIdQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetNearbyLocationsQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetPagedLocationsQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetRandomTipByTypeQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetSettingByKeyQueryHandler

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.infrastructure.mediator.Mediator
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Shared module for dependency injection
 */
val sharedModule = module {

    // Query Handlers
    factoryOf(::GetAllLocationsQueryHandler) bind IRequestHandler::class
    factoryOf(::GetLocationByIdQueryHandler) bind IRequestHandler::class
    factoryOf(::GetActiveLocationsQueryHandler) bind IRequestHandler::class
    factoryOf(::GetNearbyLocationsQueryHandler) bind IRequestHandler::class
    factoryOf(::GetPagedLocationsQueryHandler) bind IRequestHandler::class
    factoryOf(::GetWeatherByLocationIdQueryHandler) bind IRequestHandler::class
    factoryOf(::GetSettingByKeyQueryHandler) bind IRequestHandler::class
    factoryOf(::GetAllSettingsQueryHandler) bind IRequestHandler::class
    factoryOf(::GetTipsByTypeQueryHandler) bind IRequestHandler::class
    factoryOf(::GetRandomTipByTypeQueryHandler) bind IRequestHandler::class
    factoryOf(::GetAllTipTypesQueryHandler) bind IRequestHandler::class

    // Command Handlers
    factoryOf(::CreateLocationCommandHandler) bind IRequestHandler::class
    factoryOf(::UpdateLocationCommandHandler) bind IRequestHandler::class
    factoryOf(::DeleteLocationCommandHandler) bind IRequestHandler::class
    factoryOf(::AttachPhotoCommandHandler) bind IRequestHandler::class
    factoryOf(::UpdateWeatherCommandHandler) bind IRequestHandler::class
    factoryOf(::CreateSettingCommandHandler) bind IRequestHandler::class
    factoryOf(::UpdateSettingCommandHandler) bind IRequestHandler::class
    factoryOf(::CreateTipCommandHandler) bind IRequestHandler::class
    factoryOf(::CreateTipTypeCommandHandler) bind IRequestHandler::class

    // Mediator
    factory<IMediator> {
        val requestHandlers = mapOf(
            "GetAllLocationsQuery" to get<GetAllLocationsQueryHandler>(),
            "GetLocationByIdQuery" to get<GetLocationByIdQueryHandler>(),
            "GetActiveLocationsQuery" to get<GetActiveLocationsQueryHandler>(),
            "GetNearbyLocationsQuery" to get<GetNearbyLocationsQueryHandler>(),
            "GetPagedLocationsQuery" to get<GetPagedLocationsQueryHandler>(),
            "GetWeatherByLocationIdQuery" to get<GetWeatherByLocationIdQueryHandler>(),
            "GetSettingByKeyQuery" to get<GetSettingByKeyQueryHandler>(),
            "GetAllSettingsQuery" to get<GetAllSettingsQueryHandler>(),
            "GetTipsByTypeQuery" to get<GetTipsByTypeQueryHandler>(),
            "GetRandomTipByTypeQuery" to get<GetRandomTipByTypeQueryHandler>(),
            "GetAllTipTypesQuery" to get<GetAllTipTypesQueryHandler>(),
            "CreateLocationCommand" to get<CreateLocationCommandHandler>(),
            "UpdateLocationCommand" to get<UpdateLocationCommandHandler>(),
            "DeleteLocationCommand" to get<DeleteLocationCommandHandler>(),
            "AttachPhotoCommand" to get<AttachPhotoCommandHandler>(),
            "UpdateWeatherCommand" to get<UpdateWeatherCommandHandler>(),
            "CreateSettingCommand" to get<CreateSettingCommandHandler>(),
            "UpdateSettingCommand" to get<UpdateSettingCommandHandler>(),
            "CreateTipCommand" to get<CreateTipCommandHandler>(),
            "CreateTipTypeCommand" to get<CreateTipTypeCommandHandler>()
        )

        Mediator(requestHandlers, emptyMap())
    }
}