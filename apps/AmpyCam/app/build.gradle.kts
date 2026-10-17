plugins {
    id("com.android.application")
}

android {
    namespace = "dev.ampybios.cam"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ampybios.cam"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
