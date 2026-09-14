import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val appVersion = 3
val appVersionName = "0.$appVersion"

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    }
}

val storeFilePath = System.getenv("KEYSTORE_PATH")
    ?: keystoreProps.getProperty("storeFile")
val storePasswordValue = System.getenv("KEYSTORE_PASSWORD")
    ?: keystoreProps.getProperty("storePassword")
val keyAliasValue = System.getenv("KEY_ALIAS")
    ?: keystoreProps.getProperty("keyAlias")
val keyPasswordValue = System.getenv("KEY_PASSWORD")
    ?: keystoreProps.getProperty("keyPassword")

val releaseKeyFile = storeFilePath?.let { rootProject.file(it) }
val hasReleaseSigning = releaseKeyFile?.exists() == true &&
    !storePasswordValue.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank()

android {
    namespace = "app.starpath"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.starpath"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersion
        versionName = appVersionName
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeyFile
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue ?: storePasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

val copyVersionedApk = tasks.register<Copy>("copyVersionedApk") {
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    include("app-debug.apk")
    rename { "starpath-v$appVersionName-debug.apk" }
}

val copyVersionedReleaseApk = tasks.register<Copy>("copyVersionedReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    include("app-release.apk")
    rename { "starpath-v$appVersionName.apk" }
}

val copyVersionedReleaseBundle = tasks.register<Copy>("copyVersionedReleaseBundle") {
    from(layout.buildDirectory.dir("outputs/bundle/release"))
    into(layout.buildDirectory.dir("outputs/bundle/versioned"))
    include("app-release.aab")
    rename { "starpath-v$appVersionName.aab" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyVersionedApk)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyVersionedReleaseApk)
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy(copyVersionedReleaseBundle)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    testImplementation(libs.junit)
}
