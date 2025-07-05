// app/src/main/kotlin/com/x3squaredcircles/pixmap/MainActivity.kt
package com.x3squaredcircles.pixmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import com.x3squaredcircles.pixmap.core.ui.screens.AddLocationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigator(AddLocationScreen())
                }
            }
        }
    }
}