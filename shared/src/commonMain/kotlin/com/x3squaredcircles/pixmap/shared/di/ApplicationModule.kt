package com.x3squaredcircles.pixmap.shared.application.di

import com.x3squaredcircles.pixmap.shared.application.handlers.*
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
    single { GetLocationByIdQueryHandler(get()) }
    single { GetAllLocationsQueryHandler(get()) }
    single { GetLocationsByCoordinateQueryHandler(get()) }
}