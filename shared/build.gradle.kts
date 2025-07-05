//shared/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        // Android target configuration
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs(
                "src/commonMain/kotlin",
                "core-ui/src/commonMain/kotlin"  // Add this line
            )
            dependencies {
                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime.v060) // Or your current version
                implementation(libs.kotlinx.serialization.json.v173)
                // Serialization
                implementation(libs.kotlinx.serialization.json)
                // Add these for Compose UI components (using direct versions)
                implementation("androidx.compose.runtime:runtime:1.5.8")
                implementation("androidx.compose.foundation:foundation:1.5.8")
                implementation("androidx.compose.material3:material3:1.1.2")
                implementation("androidx.compose.ui:ui:1.5.8")

                // Add these for Voyager navigation (check if these work with your libs)
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.screenmodel)
                implementation(libs.voyager.koin)

                // Add Coil for AsyncImage (check if this works with your libs)
                implementation("io.coil-kt:coil-compose:2.5.0")
                // DateTime
                implementation(libs.kotlinx.datetime)

                // Dependency Injection
                implementation(libs.koin.core)

                // UUID for subscription/user management
                implementation(libs.kotlinx.uuid)

                // Ktor HTTP Client
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)

                // SQLDelight Database
                implementation(libs.sqldelight.coroutines)



                    implementation(libs.kotlinx.uuid)

            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.koin.test)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)

                // Android-specific dependencies
                implementation(libs.google.play.billing)

                // Ktor Android specific
                implementation(libs.ktor.client.android)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                // SQLDelight Android driver
                implementation(libs.sqldelight.driver.android)

                // Android Location Services
                implementation(libs.play.services.location)

                // Android Notifications
                implementation(libs.androidx.core.ktx.v1131)
                // Add Android-specific Compose dependencies
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.ui.tooling.preview)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.turbine)
                implementation(libs.mockk)
            }
        }
    }
}

android {
    namespace = "com.x3squaredcircles.pixmap.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.text.android)

}

sqldelight {
    databases {
        create("PixMapDatabase") {
            packageName.set("com.x3squaredcircles.pixmap.shared.infrastructure.data")
        }
    }
}