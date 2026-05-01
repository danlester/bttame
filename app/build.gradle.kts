import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.ideonate.whistle"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ideonate.whistle"
        minSdk = 31
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"
    }

    val releaseStoreFilePath = localProps.getProperty("whistleStoreFile")
    val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = localProps.getProperty("whistleStorePassword")
                keyAlias = localProps.getProperty("whistleKeyAlias")
                keyPassword = localProps.getProperty("whistleKeyPassword")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
}
