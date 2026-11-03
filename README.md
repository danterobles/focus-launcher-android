# Focus Launcher

Launcher Android local y offline que permite ocultar apps **solo dentro de Focus** y proteger su configuración con un PIN opcional. No es un bloqueo parental: el propietario puede cambiar de Home, borrar datos o desinstalar. Documentación normativa: [PRD](PRD.md), [ARCHITECTURE](ARCHITECTURE.md), [DATA](DATA.md), [TASKS](TASKS.md), [CLAUDE](CLAUDE.md).

Estado: **T01–T10 implementadas y validadas en emuladores (API 31, 34, 35)**; T09 y T10 tienen pendientes que requieren dispositivos reales (ver `docs/validation/T09.md` y `docs/validation/T10.md`). Queda T11 (entrega).

## Requisitos
- JDK 17 (`JAVA_HOME`), Android SDK con plataforma 37 y build-tools 36+ (`ANDROID_HOME`).
- Versiones exactas: [docs/VERSIONS.md](docs/VERSIONS.md).

## Build y pruebas
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # requiere emulador/dispositivo de prueba
```
```bash
./gradlew :app:verifyReleaseManifest                # audita el manifiesto combinado del release (permisos, exports, backups)
# Mediciones de rendimiento (RNF-04) sobre código no depurable, con el fixture de 200 entradas instalado:
./gradlew -p fixtures/many-launchers assembleDebug && adb install -r fixtures/many-launchers/build/outputs/apk/debug/many-launchers-debug.apk
./gradlew :app:connectedBenchmarkAndroidTest -PperfBuild=true -Pandroid.testInstrumentationRunnerArguments.class=com.focuslauncher.perf.PerformanceBudgetTest
adb logcat -d -s FocusPerf                           # p50/p95 por métrica
```
Usar siempre el wrapper (`./gradlew`). No instalar Focus en un teléfono personal para pruebas automáticas.

## Registro de equipos de prueba
| Equipo | API / build | Uso |
| --- | --- | --- |
| Emulador `Pixel_6_API_35` (google_apis arm64 r9) | 35 · `AE3A.240806.043` | Referencia de rendimiento (RNF-04), matriz completa, perfiles |
| Emulador `Pixel_8_Pro_API_34` (google_apis_playstore arm64) | 34 · `UE1A.230829.036.A4` | Matriz completa |
| Emulador `Pixel_6_API_31` (google_apis arm64 r11) | 31 · `SE1A.220630.001.A1` | Matriz completa (mínimo soportado) |
| **Sin validar** | última API estable, Samsung One UI, dispositivos físicos | Pendiente (T10) |

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
