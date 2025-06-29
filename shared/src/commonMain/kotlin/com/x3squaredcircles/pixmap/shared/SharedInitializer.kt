// shared/src/commonMain/kotlin/com/x3squaredcircles/pixmap/shared/SharedInitializer.kt
package com.x3squaredcircles.pixmap.shared

import com.x3squaredcircles.pixmap.shared.infrastructure.di.sharedModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Initializer for shared KMM module
 */
object SharedInitializer {

    fun initialize(appDeclaration: KoinAppDeclaration = {}) {
        startKoin {
            appDeclaration()
            modules(sharedModule)
        }
    }
}