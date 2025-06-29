package com.x3squaredcircles.pixmap.shared.di

import org.koin.dsl.module

/**
 * Master module that includes all shared modules
 */
val allSharedModules = module {
    includes(
        completeDomainModule,
        completeApplicationModule,
        servicesModule
    )
}