plugins {
    id("com.android.application")
}
android {
    namespace = "dev.ampybios.core"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ampybios.core"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            // Exactly the same themes as the AmpybiOS host-side configuration.
            assets.srcDirs("../../../config/themes")
        }
    }
}
