package com.x3squaredcircles.pixmap.shared.application.di

import com.x3squaredcircles.pixmap.shared.application.handlers.*
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.AttachPhotoCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.CreateLocationCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.commands.UpdateLocationCommandHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetAllLocationsQueryHandler
import com.x3squaredcircles.pixmap.shared.application.handlers.queries.GetLocationByIdQueryHandler
import org.koin.dsl.module

/**
 * Dependency injection module for application layer
 */
val applicationModule = module {

    // Command Handlers
    single { CreateLocationCommandHandler(get()) }
    single { UpdateLocationCommandHandler(get()) }
    single { DeleteLocationCommandHandler(get()) }
    single { AttachPhotoCommandHandler(get()) }

    // Query Handlers
    single { GetLocationByIdQueryHandler(
        get(),
        mediator = TODO()
    ) }
    single { GetAllLocationsQueryHandler(get(), mediator = TODO()) }
    single { GetLocationsByCoordinateQueryHandler(get()) }
}