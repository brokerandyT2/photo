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
            dependencies {
                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // Serialization
                implementation(libs.kotlinx.serialization.json)

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
                implementation("com.google.android.gms:play-services-location:21.0.1")

                // Android Notifications
                implementation("androidx.core:core-ktx:1.13.1")
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

sqldelight {
    databases {
        create("PixMapDatabase") {
            packageName.set("com.x3squaredcircles.pixmap.shared.infrastructure.data")
        }
    }
}