plugins { id("com.android.application") }

android {
    namespace = "dev.ampybios.rec"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.ampybios.rec"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0-dev"
    }
    // Tests exercise the production ZIP validator on a plain JVM.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
