package com.x3squaredcircles.pixmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.x3squaredcircles.pixmap.android.di.androidPlatformModule
import com.x3squaredcircles.pixmap.shared.SharedInitializer
import com.x3squaredcircles.pixmap.ui.theme.PixMapTheme
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Koin DI if not already initialized
        if (GlobalContext.getOrNull() == null) {
            SharedInitializer.initialize(androidPlatformModule)
            GlobalContext.get().androidContext(this)
        }

        enableEdgeToEdge()
        setContent {
            PixMapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainContent()
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    // Main content will be implemented here
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PixMapTheme {
        MainContent()
    }
}