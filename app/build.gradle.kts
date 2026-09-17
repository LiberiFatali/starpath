import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val appVersionCode = 8
val appVersionName = "0.8"

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

val releaseKeyFile = storeFilePath?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
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
        versionCode = 8
        versionName = "0.8"
    }

    buildFeatures {
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
    doFirst {
        layout.buildDirectory.dir("outputs/apk/versioned").get().asFile
            .listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
    }
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    include("app-debug.apk")
    rename { "starpath-v$appVersionName-debug.apk" }
}

val copyVersionedReleaseApk = tasks.register<Copy>("copyVersionedReleaseApk") {
    doFirst {
        layout.buildDirectory.dir("outputs/apk/versioned").get().asFile
            .listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
    }
    from(layout.buildDirectory.dir("outputs/apk/release"))
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    include("app-release.apk", "app-release-unsigned.apk")
    rename { filename ->
        if (filename.contains("unsigned")) {
            "starpath-v$appVersionName-unsigned.apk"
        } else {
            "starpath-v$appVersionName.apk"
        }
    }
}

val copyVersionedReleaseBundle = tasks.register<Copy>("copyVersionedReleaseBundle") {
    doFirst {
        layout.buildDirectory.dir("outputs/bundle/versioned").get().asFile
            .listFiles { f -> f.name.endsWith(".aab") }?.forEach { it.delete() }
    }
    from(layout.buildDirectory.dir("outputs/bundle/release"))
    into(layout.buildDirectory.dir("outputs/bundle/versioned"))
    include("app-release.aab", "app-release-unsigned.aab")
    rename { filename ->
        if (filename.contains("unsigned")) {
            "starpath-v$appVersionName-unsigned.aab"
        } else {
            "starpath-v$appVersionName.aab"
        }
    }
}

// Versioned copies are publish-only (release.yml checksums + docs).
// Local builds stay on AGP defaults so ./gradlew assemble* does not
// recreate outputs/apk/versioned or outputs/bundle/versioned.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    tasks.matching { it.name == "assembleDebug" }.configureEach {
        finalizedBy(copyVersionedApk)
    }

    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(copyVersionedReleaseApk)
    }

    tasks.matching { it.name == "bundleRelease" }.configureEach {
        finalizedBy(copyVersionedReleaseBundle)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    testImplementation(libs.junit)
}
