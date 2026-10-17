plugins {
    id("com.android.application")
}
android {
    namespace = "dev.ampybios.raw"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.ampybios.raw"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("../../../config/themes")
        }
    }
}
