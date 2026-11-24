plugins { id("com.android.application") }

android {
    namespace = "com.baltdh.dailyoffice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.baltdh.dailyoffice"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    // Bundled model: OCR is available immediately and works offline after installation.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
