package com.x3squaredcircles.pixmap

import android.app.Application
import com.x3squaredcircles.pixmap.android.di.androidPlatformModule
import com.x3squaredcircles.pixmap.shared.SharedInitializer
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PixMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@PixMapApplication)
            modules(androidPlatformModule)
        }

        SharedInitializer.initialize()
    }
}