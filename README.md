# Focus Launcher

Launcher Android local y offline que permite ocultar apps **solo dentro de Focus** y proteger su configuración con un PIN opcional. No es un bloqueo parental: el propietario puede cambiar de Home, borrar datos o desinstalar. Documentación normativa: [PRD](PRD.md), [ARCHITECTURE](ARCHITECTURE.md), [DATA](DATA.md), [TASKS](TASKS.md), [CLAUDE](CLAUDE.md).

Estado: **T01 (base del proyecto)**. Aún no hay funcionalidad de launcher.

## Requisitos
- JDK 17 (`JAVA_HOME`), Android SDK con plataforma 37 y build-tools 36+ (`ANDROID_HOME`).
- Versiones exactas: [docs/VERSIONS.md](docs/VERSIONS.md).

## Build y pruebas
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # requiere emulador/dispositivo de prueba
```
Usar siempre el wrapper (`./gradlew`). No instalar Focus en un teléfono personal para pruebas automáticas.

## Registro de equipos de prueba
| Equipo | API | Uso |
| --- | --- | --- |
| Emulador `Pixel_8_Pro_API_34` | 34 | T01 (instrumentación) |

Evidencia de validación: `docs/validation/`.
