package com.x3squaredcircles.pixmap.shared.di

import com.x3squaredcircles.pixmap.shared.application.di.applicationModule
import com.x3squaredcircles.pixmap.shared.application.di.mediatorModule
import org.koin.dsl.module

/**
 * Complete application module that includes all application-related modules
 */
val completeApplicationModule = module {
    includes(
        applicationModule,
        mediatorModule
    )
}