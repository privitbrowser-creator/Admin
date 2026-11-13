plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.ihk909.admin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ihk909.admin"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0"

        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        python {
            version = "3.11"
            pip {
                install("flask==3.0.0")
                install("flask-sock==0.7.0")
                install("requests==2.31.0")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.android.tools.build:apksig:8.5.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}