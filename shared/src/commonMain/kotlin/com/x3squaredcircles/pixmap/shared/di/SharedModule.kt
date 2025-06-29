package com.x3squaredcircles.pixmap.shared.di

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