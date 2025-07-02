//app/src/main/kotlin/com/x3squaredcircles/pixmap/PixMapApplication.kt
package com.x3squaredcircles.pixmap

import android.app.Application
import com.x3squaredcircles.pixmap.di.allAndroidModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Main application class for dependency injection initialization
 */
class PixMapApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            // Enable Koin Android features
            androidLogger(Level.ERROR)
            androidContext(this@PixMapApplication)

            // Load all modules
            modules(allAndroidModules)
        }
    }
}