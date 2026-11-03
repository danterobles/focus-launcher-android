# Focus Launcher

Launcher Android local y offline que permite ocultar apps **solo dentro de Focus** y proteger su configuración con un PIN opcional. No es un bloqueo parental: el propietario puede cambiar de Home, borrar datos o desinstalar. Documentación normativa: [PRD](PRD.md), [ARCHITECTURE](ARCHITECTURE.md), [DATA](DATA.md), [TASKS](TASKS.md), [CLAUDE](CLAUDE.md).

Estado: **T01–T08 implementadas** (validadas en emulador API 34); quedan T09 (accesibilidad), T10 (validación en equipos/OEM y rendimiento) y T11 (entrega).

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

## Uso básico
1. Instala el APK y ábrelo: verás el aviso de alcance. Acéptalo (puedes elegir «usar como pantalla de inicio» o probar sin cambiarla).
2. Para elegir Focus como Home: botón «Usar Focus como pantalla de inicio» (diálogo oficial de Android) o Ajustes → Aplicaciones → Apps predeterminadas → App de inicio.
3. Configuración de Focus → interruptores Visible/Oculta por paquete. Ocultar solo cambia lo que muestra Focus.
4. Configuración → Seguridad → PIN opcional (6–12 dígitos).

## PIN olvidado o configuración dañada
El PIN no se puede recuperar (sin PIN maestro ni pregunta secreta). Desde Android: (1) cambia de launcher (Ajustes → Aplicaciones → Apps predeterminadas → App de inicio); (2) Ajustes → Aplicaciones → Focus Launcher → Almacenamiento → **Borrar datos** (o desinstala); (3) vuelve a elegir Focus si quieres. Se pierden reglas, aviso y PIN. La pantalla de Ayuda de Focus tiene botones directos a esos ajustes y funciona siempre, con o sin PIN.

## Volver al launcher anterior
Ayuda → «Abrir ajustes de pantalla de inicio» y elige tu launcher anterior.
