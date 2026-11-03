# Registro de versiones y entorno (T01)

Verificado el 2026-11-03. Solo versiones estables (sin alpha/beta/RC ni rangos dinámicos). Fuente de verdad: `gradle/libs.versions.toml` y `gradle/wrapper/gradle-wrapper.properties`.

| Componente | Versión | Fuente consultada |
| --- | --- | --- |
| Gradle (wrapper, con SHA-256) | 9.6.1 | https://services.gradle.org/versions/all (AGP 9.5 exige ≥ 9.6.0; 9.8.x omitido: aviso de deprecaciones con AGP 9.4.1 en las notas oficiales de AGP) |
| Android Gradle Plugin | 9.5.0 | https://dl.google.com/dl/android/maven2 y https://developer.android.com/build/releases/gradle-plugin |
| Kotlin / plugin Compose | 2.4.21 | Maven Central `kotlin-gradle-plugin` |
| KSP | 2.3.13 | Maven Central `symbol-processing-gradle-plugin` |
| Hilt | 2.60.1 | Maven Central `hilt-android` |
| androidx.hilt:hilt-navigation-compose | 1.4.0 | Google Maven |
| Compose BOM | 2026.09.00 | Google Maven |
| Activity Compose | 1.13.0 | Google Maven |
| Lifecycle | 2.11.0 | Google Maven |
| Core KTX | 1.19.1 | Google Maven |
| DataStore | 1.3.0 | Google Maven |
| Protobuf javalite / protoc | 4.36.2 (4.37 solo RC) | Maven Central |
| Plugin Protobuf Gradle | 0.10.0 | Maven Central |
| Coroutines | 1.11.0 | Maven Central |
| JUnit / Turbine | 4.13.2 / 1.2.1 | Maven Central |

## SDK
- `minSdk = 31`.
- `compileSdk = 37`: exigido por las dependencias (Compose UI 1.12.1 y Core KTX 1.19.1 requieren API 37; AGP 9.5 soporta hasta API 37).
- `targetSdk = 36`: se mantiene un nivel por debajo para no activar cambios de comportamiento de la API 37 sin validarlos. **Revisar en T10** contra la última versión estable disponible.

## Equipos de prueba (T10)
Ver el registro completo en `docs/validation/T10.md` §1 y en el README. Emulador: Android Emulator 37.1.11.0 sobre Apple M2 Max (32 GB, macOS 27.2). Imágenes: API 31 `google_apis` rev. 11, API 34 `google_apis_playstore`, API 35 `google_apis` rev. 9 (arm64-v8a).

## Entorno de desarrollo usado
- macOS (Darwin 27.2.0), JDK Zulu 17.0.11, Android SDK en `~/Library/Android/sdk` (plataformas 31 a 37, build-tools hasta 36.0.0).
- Emulador de prueba: AVD `Pixel_8_Pro_API_34` (Android 14).

## Comandos verificados
Ver resultados en `docs/validation/T01.md`.
