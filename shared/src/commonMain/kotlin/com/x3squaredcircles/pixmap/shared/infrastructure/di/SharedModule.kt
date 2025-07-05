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
import com.x3squaredcircles.pixmap.shared.application.queries.GetActiveLocationsQueryHandler
import com.x3squaredcircles.pixmap.shared.application.queries.GetWeatherByLocationIdQueryHandler
import com.x3squaredcircles.pixmap.shared.application.queries.GetTipsByTypeQueryHandler

import com.x3squaredcircles.pixmap.shared.application.interfaces.IMediator
import com.x3squaredcircles.pixmap.shared.application.interfaces.IRequestHandler
import com.x3squaredcircles.pixmap.shared.infrastructure.mediator.Mediator
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Shared module for dependency injection
 */
val sharedModule = module {

    // Query Handlers
    factory<IRequestHandler<*, *>> { GetAllLocationsQueryHandler(get(), get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetLocationByIdQueryHandler(get(), get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetActiveLocationsQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetNearbyLocationsQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetPagedLocationsQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetWeatherByLocationIdQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetSettingByKeyQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetAllSettingsQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetTipsByTypeQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetRandomTipByTypeQueryHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { GetAllTipTypesQueryHandler(get()) } bind IRequestHandler::class

    // Command Handlers
    factory<IRequestHandler<*, *>> { CreateLocationCommandHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { UpdateLocationCommandHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { DeleteLocationCommandHandler(get(), get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { AttachPhotoCommandHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { UpdateWeatherCommandHandler(get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { CreateSettingCommandHandler(get(), get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { UpdateSettingCommandHandler(get(), get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { CreateTipCommandHandler(get(), get()) } bind IRequestHandler::class
    factory<IRequestHandler<*, *>> { CreateTipTypeCommandHandler(get(), get()) } bind IRequestHandler::class

    // Mediator - simplified without the complex map
    single<IMediator> {
        Mediator(emptyMap(), emptyMap())
    }
}