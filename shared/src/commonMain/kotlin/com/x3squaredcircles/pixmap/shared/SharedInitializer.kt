package com.x3squaredcircles.pixmap.shared

import com.x3squaredcircles.pixmap.shared.di.completeSharedModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Initializer for the shared module
 */
object SharedInitializer {

    fun initialize(platformModule: org.koin.core.module.Module = module { }) {
        startKoin {
            modules(
                completeSharedModule,
                platformModule
            )
        }
    }
}