package com.x3squaredcircles.pixmap.shared.di

import com.x3squaredcircles.pixmap.shared.application.di.applicationModule
import com.x3squaredcircles.pixmap.shared.application.di.mediatorModule
import org.koin.dsl.module

/**
 * Main shared module that aggregates all domain modules
 */
val sharedModule = module {
    includes(
        locationDomainModule,
        applicationModule,
        mediatorModule,
        servicesModule
    )
}