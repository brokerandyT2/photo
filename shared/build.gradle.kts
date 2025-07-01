plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}


kotlin {
    android {
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

                // Android-specific billing (business logic still in commonMain)
                implementation(libs.google.play.billing)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}