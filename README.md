# Focus Launcher

Launcher Android **local y offline** para organización personal o familiar: decides qué aplicaciones aparecen en *Focus* y proteges esa configuración con un PIN opcional. Sin cuentas, backend, telemetría ni anuncios.

> **Ocultar una aplicación solo cambia lo que muestra Focus.** Puede seguir abriéndose desde notificaciones, enlaces, Ajustes de Android, Recientes, asistentes u otro launcher. El PIN protege la configuración de Focus, no el bloqueo de Android. **No es un bloqueo parental:** el propietario del teléfono puede cambiar de launcher, borrar los datos de Focus o desinstalarlo.

**Estado: MVP candidato (versión 0.1.0), validado en emuladores (API 31, 34 y 35).** No se ha validado en dispositivos físicos, Samsung One UI ni en la última versión de Android; ver las [notas de la versión](docs/RELEASE-NOTES.md#limitaciones-y-pendientes-de-validación).

Teléfonos físicos (Honor, Redmi…): [guía de instalación y desinstalación](docs/INSTALL-PHYSICAL-DEVICES.md). Una vez instalado: **[OPERACION.md](OPERACION.md)** — ocultar apps, dejar solo algunas y configurar el PIN, con capturas.

Documentos: [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [DATA](DATA.md) · [TASKS](TASKS.md) (incluye la trazabilidad final) · [CLAUDE](CLAUDE.md) · [notas de la versión](docs/RELEASE-NOTES.md) · [evidencia de validación](docs/validation/).

---

## 1. Requisitos
- Teléfono o emulador con **Android 12 o superior** (`minSdk 31`), perfil de usuario normal (personal).
- Para compilar: **JDK 17** (`JAVA_HOME`) y **Android SDK** con la plataforma 37 y las build-tools 36 (`ANDROID_HOME`). Versiones exactas de todo el toolchain: [docs/VERSIONS.md](docs/VERSIONS.md).
- `adb` (parte de `platform-tools`) para instalar desde la línea de comandos.

## 2. Obtener el APK de prueba
```bash
git clone <este-repositorio> focus-launcher && cd focus-launcher
export ANDROID_HOME="$HOME/Library/Android/sdk"        # ajusta la ruta a tu SDK
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"      # macOS; en otros sistemas, la ruta de tu JDK 17
./scripts/build-test-apk.sh                             # → dist/focus-launcher-0.1.0-test.apk (+ .sha256)
```
El script compila el release con R8, **audita el manifiesto combinado** (falla si aparece un permiso o componente exportado no permitido), alinea, firma con tu clave de **depuración** local y verifica la firma.

> El APK es **solo de pruebas**: está firmado con la clave de depuración de Android (pública, contraseña `android`). No es válido para ninguna tienda. No se versiona ninguna clave ni APK; el hash de tu APK será distinto del de otras máquinas porque la clave de depuración es distinta.

## 3. Instalar
En un **emulador o teléfono de pruebas** (no en un teléfono personal para pruebas automáticas):
```bash
adb install -r dist/focus-launcher-0.1.0-test.apk
adb shell am start -n com.focuslauncher/.MainActivity     # o ábrelo desde el cajón del teléfono («Focus Launcher»)
```
Para teléfonos físicos de gama media (Honor/MagicOS, Redmi/MIUI-HyperOS) con ajustes de fabricante, desinstalación completa y registro de pruebas, sigue [docs/INSTALL-PHYSICAL-DEVICES.md](docs/INSTALL-PHYSICAL-DEVICES.md).

Sin `adb`: copia el APK al teléfono, ábrelo desde el administrador de archivos y permite la instalación desde esa fuente cuando Android lo pida.

## 4. Elegir Focus como pantalla de inicio
1. Al abrirlo verás el **aviso de alcance**. Léelo y elige:
   - «Entendido: **usar Focus como pantalla de inicio**» → Android muestra su diálogo oficial «¿Establecer Focus Launcher como app de inicio?»: selecciona *Focus Launcher* y **Establecer como predeterminada**; o
   - «Entendido: **probar Focus sin cambiar mi inicio**» → puedes probarlo sin cambiar nada y pedirlo después desde Inicio con «Usar Focus como pantalla de inicio».
   Si cancelas el diálogo, Focus sigue funcionando y **no se vuelve a pedir** solo.
2. Comprobación: pulsa el botón Inicio del teléfono; debe abrirse Focus. En el emulador/ADB:
   `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`

## 5. Uso
| Quiero… | Cómo |
| --- | --- |
| Abrir una app | Toca su icono en Inicio (las 8 primeras) o en **Todas las aplicaciones** |
| Buscar | **Buscar** → escribe; no distingue mayúsculas ni acentos y solo busca entre las apps visibles |
| Ocultar o mostrar apps | **Configuración de Focus** → interruptor *Visible / Oculta* de cada paquete (todas las actividades de un paquete comparten el interruptor). Cada cambio se guarda al instante, con indicador de guardado |
| Proteger la configuración | Configuración → **Seguridad** → *Activar PIN* (6 a 12 dígitos). Desde entonces Configuración pide el PIN; la sesión dura como máximo 2 minutos y se cierra al salir de Focus, pulsar Inicio o apagar la pantalla |
| Cambiar / quitar el PIN | Configuración → Seguridad (pide el PIN; quitarlo y restablecer piden confirmación explícita) |
| Ayuda | **Ayuda** en Inicio (siempre accesible, con o sin PIN) |

Tras 5 intentos de PIN incorrectos hay esperas de 30 s, 60 s, 2 min, 4 min, 8 min y 15 min. Si Focus se cierra durante una espera, **empieza de nuevo por completo**. Las apps nuevas aparecen **visibles por defecto**.

## 6. Reiniciar, olvidar el PIN o recuperar
- **Reiniciar el teléfono** no pierde nada: reglas, PIN, intentos y espera persisten (cifrados en el almacenamiento privado de Focus).
- **PIN olvidado o configuración dañada:** el PIN **no se puede recuperar** (no hay PIN maestro ni pregunta secreta) y Focus no restablece nada por sí mismo sin PIN. Desde Android:
  1. **Cambia de launcher:** Ayuda de Focus → «Abrir ajustes de pantalla de inicio» (o Ajustes → Aplicaciones → Apps predeterminadas → App de inicio) y elige otro.
  2. **Borra los datos de Focus:** Ayuda → «Abrir información de la app de Focus» → Almacenamiento → **Borrar datos** (o desinstala Focus). Se pierden reglas, aviso inicial y PIN.
  3. Vuelve a elegir Focus como pantalla de inicio si quieres seguir usándolo.
- **Restablecer sabiendo el PIN:** Configuración → Seguridad → *Restablecer configuración* (pide el PIN y una confirmación; borra reglas, aviso inicial y PIN).
- La pantalla de **Ayuda** tiene botones directos a esos ajustes y, si tu fabricante no los ofrece, muestra la ruta manual.

## 7. Volver al launcher anterior
Ayuda → «Abrir ajustes de pantalla de inicio» y elige tu launcher anterior (o Ajustes → Aplicaciones → Apps predeterminadas → App de inicio). Para quitar Focus del todo, desinstálalo desde Ajustes.

---

## 8. Compilar, probar y medir (desarrollo)
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug          # 183 pruebas unitarias
./gradlew :app:connectedDebugAndroidTest                  # requiere emulador/dispositivo de PRUEBA conectado
./gradlew :app:assembleRelease :app:verifyReleaseManifest # audita el manifiesto combinado del release

# Rendimiento (RNF-04) sobre código no depurable, con el fixture de 200 entradas instalado:
./gradlew -p fixtures/many-launchers assembleDebug && adb install -r fixtures/many-launchers/build/outputs/apk/debug/many-launchers-debug.apk
./gradlew :app:connectedBenchmarkAndroidTest -PperfBuild=true -Pandroid.testInstrumentationRunnerArguments.class=com.focuslauncher.perf.PerformanceBudgetTest
adb logcat -d -s FocusPerf                                # p50/p95 por métrica
```
Usa siempre el wrapper (`./gradlew`). Las pruebas instrumentadas **desinstalan** la app al terminar y escriben en su propio almacén aislado (nunca tocan el estado real). Las pruebas de accesibilidad ATF solo son efectivas en API 34+ (se omiten antes; no cuentan como superadas). No instales Focus como Home ni borres datos en un teléfono personal para obtener resultados de pruebas.

## 9. Equipos de prueba registrados
| Equipo | API / build | Uso |
| --- | --- | --- |
| Emulador `Pixel_6_API_35` (google_apis arm64 r9) | 35 · `AE3A.240806.043` | Referencia de rendimiento (RNF-04), matriz completa, perfiles de trabajo y Espacio privado |
| Emulador `Pixel_8_Pro_API_34` (google_apis_playstore arm64) | 34 · `UE1A.230829.036.A4` | Matriz completa |
| Emulador `Pixel_6_API_31` (google_apis arm64 r11) | 31 · `SE1A.220630.001.A1` | Matriz completa (mínimo soportado) |
| **Sin validar** | última API estable, Samsung One UI, dispositivos físicos | Pendiente |

Host de las pruebas: Apple M2 Max, 32 GB, macOS 27.2, Android Emulator 37.1.11.0. Evidencia por tarea en [`docs/validation/`](docs/validation/) (T01–T10).

## 10. Estructura
```
app/                     módulo único :app (com.focuslauncher): core/, domain/, data/, presentation/
design/                  arte original del icono
docs/                    VERSIONS.md, RELEASE-NOTES.md, validation/ (evidencia T01–T10)
fixtures/many-launchers/ proyecto aparte: 200 entradas launcher para medir RNF-04
scripts/                 build-test-apk.sh
```
