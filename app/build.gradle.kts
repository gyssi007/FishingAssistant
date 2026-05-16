plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")
}

android {

    namespace = "com.fishtime.assistant"

    compileSdk = 34

    defaultConfig {

        applicationId = "com.fishtime.assistant"

        minSdk = 26

        targetSdk = 34

        versionCode = 1

        versionName = "1.0"
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

    kotlinOptions {

        jvmTarget = "17"
    }
}

dependencies {

    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    implementation 'com.google.code.gson:gson:2.10.1'
}
