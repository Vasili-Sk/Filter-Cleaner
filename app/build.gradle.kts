plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("FILTER_CLEANER_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("FILTER_CLEANER_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FILTER_CLEANER_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FILTER_CLEANER_KEY_PASSWORD").orNull
val signingValues = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = signingValues.all { !it.isNullOrBlank() }
require(hasReleaseSigning || signingValues.all { it.isNullOrBlank() }) {
    "Set all four FILTER_CLEANER signing environment variables, or leave all four unset."
}

android {
    namespace = "dev.filtercleaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.filtercleaner"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storeType = "PKCS12"
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
