import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.okornilov.glucose"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.okornilov.glucose"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    // Ключ релизов — вне репозитория: путь и пароли в ~/.gradle/gradle.properties (GLUCOSE_*).
    // Без них (сборка из исходников на другом компьютере) release подписывается debug-ключом сборщика.
    val releaseStore = providers.gradleProperty("GLUCOSE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("GLUCOSE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("GLUCOSE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("GLUCOSE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Экран «Сахар» в Android Auto
    implementation("androidx.car.app:app:1.4.0")
    // Сторож мониторинга — периодическая проверка системным планировщиком
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
