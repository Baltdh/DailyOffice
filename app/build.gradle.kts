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

    buildTypes {
        getByName("debug") {
            // Isola a versão de teste para não colidir com a instalação existente
            // assinada por outra chave. Os dados do app já instalado são preservados.
            applicationIdSuffix = ".teste"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

configurations.configureEach {
    // Kotlin 1.8+ folds these JDK extensions into kotlin-stdlib; older transitive
    // ML Kit dependencies may still request the split 1.6 artifacts.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    // Bundled model: OCR is available immediately and works offline after installation.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
