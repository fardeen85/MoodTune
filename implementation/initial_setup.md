# Android Project Initial Setup Template

This guide provides a baseline configuration for a modern Android project using **Compose**, **Koin**, **Ktor**, **Room**, **Navigation 3**, and **KSP**.

## 1. Version Catalog (`gradle/libs.versions.toml`)

Add these to your `[versions]`, `[libraries]`, and `[plugins]` sections. Use the latest stable versions from Maven Central or Google's Maven repository.

```toml
[versions]
# Core
kotlin = "[LATEST_KOTLIN_VERSION]"
agp = "[LATEST_AGP_VERSION]"
ksp = "[MATCHING_KSP_VERSION]" # Must match your Kotlin version

# DI & Network
koin = "[LATEST_KOIN_VERSION]"
ktor = "[LATEST_KTOR_VERSION]"

# Persistence & UI
room = "[LATEST_ROOM_VERSION]"
navigation3 = "[LATEST_NAV3_VERSION]"
splashscreen = "[LATEST_SPLASH_VERSION]"

[libraries]
# DI - Koin
koin-bom = { group = "io.insert-koin", name = "koin-bom", version.ref = "koin" }
koin-androidx-compose = { group = "io.insert-koin", name = "koin-androidx-compose" }

# Network - Ktor
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }

# Database - Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Navigation & Splash
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }

[plugins]
google-ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

## 2. Global Configuration

### `gradle.properties`
Suppresses warnings related to KSP and the new built-in Kotlin DSL in newer AGP versions:
```properties
android.disallowKotlinSourceSets=false
```

### `build.gradle.kts` (Root)
```kotlin
plugins {
    alias(libs.plugins.google.ksp) apply false
}
```

## 3. Module Configuration (`app/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.google.ksp)
}

dependencies {
    // DI
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)

    // Network
    implementation(libs.ktor-client-core)
    implementation(libs.ktor-client-okhttp)
    implementation(libs.ktor-client-logging)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Splash
    implementation(libs.androidx.core.splashscreen)
}
```

## 4. Architectural Boilerplate

### Ktor Client Module (Koin)
```kotlin
val networkModule = module {
    single {
        HttpClient(OkHttp) {
            install(Logging) { level = LogLevel.ALL }
            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
    }
}
```

### Premium Dark Theme (Material 3)
1.  **Colors**: Map your specific brand palette to the `darkColorScheme`.
2.  **Elevation**: Use tonal layering (slightly lighter blacks) to communicate depth in OLED-friendly interfaces.
3.  **Fonts**: Place font files in your central UI module's `res/font` folder to ensure resources are properly resolved.
4.  **System UI**: Use `SideEffect` in your `Theme.kt` to keep status bar colors consistent with your background.
