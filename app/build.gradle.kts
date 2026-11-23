plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.ghsearchlite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.komilite"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-komi"
    }

    signingConfigs {
        create("release") {
            storeFile = file("ghsearch.jks")
            storePassword = "ghsearch2026"
            keyAlias = "ghsearch"
            keyPassword = "ghsearch2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
