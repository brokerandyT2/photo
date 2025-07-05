// app/src/main/kotlin/com/x3squaredcircles/pixmap/MainActivity.kt
package com.x3squaredcircles.pixmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.x3squaredcircles.pixmap.ui.screens.AddLocationScreen
import com.x3squaredcircles.pixmap.ui.theme.PixMapTheme
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Koin DI
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule)
        }

        enableEdgeToEdge()

        setContent {
            PixMapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Navigator(
                        screen = AddLocationScreen(),
                        modifier = Modifier.padding(innerPadding)
                    ) { navigator ->
                        SlideTransition(navigator)
                    }
                }
            }
        }
    }
}