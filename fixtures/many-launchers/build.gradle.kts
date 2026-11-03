// Fixture de pruebas (NO forma parte de la app): un paquete con 200 entradas launcher reales para
// medir RNF-04. Versión de AGP idéntica a la de la app (ver docs/VERSIONS.md).
plugins {
    id("com.android.application") version "9.5.0"
}

android {
    namespace = "com.focuslauncher.fixture200"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.focuslauncher.fixture200"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}
