plugins {
    alias(libs.plugins.android.application) apply false
}

// Pin the Kotlin version used by AGP 9 built-in Kotlin (no kotlin-android plugin).
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}
