plugins {
    alias(libs.plugins.android.application)
}

val appVersion = 2

android {
    namespace = "app.starpath"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.starpath"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersion
        versionName = "0.$appVersion"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    testImplementation(libs.junit)
}
