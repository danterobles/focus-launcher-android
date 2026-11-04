# Focus Launcher — Instalación y desinstalación en teléfonos físicos de prueba

Guía paso a paso para instalar, probar y **desinstalar por completo** el APK de prueba de Focus Launcher (versión 0.1.0) en teléfonos físicos de gama media, con notas específicas para **Redmi / Xiaomi (MIUI y HyperOS)** y **Honor (MagicOS)**.

> ### Qué está y qué no está verificado en esta guía
> - **Verificado** (emuladores Android 12, 14 y 15 y checkout limpio): el APK, los comandos `adb`, el flujo de instalación, la selección de Home, el uso, el reinicio, el PIN olvidado, «Borrar datos» y la desinstalación estándar de Android.
> - **No verificado en un Honor ni en un Redmi reales:** los nombres exactos de menús, las pantallas de seguridad de cada fabricante y sus ajustes de energía. Son las rutas habituales de cada marca, pero **cambian según la versión de MagicOS, MIUI o HyperOS y el idioma**. Si una ruta no coincide, busca el ajuste por su nombre en el buscador de *Ajustes*. Si encuentras una diferencia, anótala en la sección 10 para corregir esta guía.
> - Focus **no ha sido validado en hardware real** (ver `docs/RELEASE-NOTES.md`). Esta guía existe precisamente para poder hacerlo.

---

## 0. Antes de empezar

- **Usa un teléfono de pruebas dedicado**, sin cuentas personales, fotos ni datos reales. Focus se instala como pantalla de inicio y cambia cómo se ve el teléfono; si algo falla, el teléfono debe poder restablecerse sin pérdida.
- El APK es **solo de pruebas**: está firmado con la clave de **depuración** de Android (pública). No es válido para tiendas y Android lo tratará como «de origen desconocido».
- Focus **no es un bloqueo parental**: cualquiera con acceso al teléfono puede cambiar de launcher, borrar los datos de Focus o desinstalarlo (ver README). Eso es lo que usarás para desinstalar.
- Requisito: **Android 12 o superior** (`minSdk 31`). Casi todos los Redmi y Honor de gama media vendidos desde 2022 lo cumplen. Compruébalo en *Ajustes → Acerca del teléfono → Versión de Android*.

### Lo que necesitas
| Qué | Para qué |
| --- | --- |
| El archivo `focus-launcher-0.1.0-test.apk` | Se genera con `./scripts/build-test-apk.sh` (README §2). SHA-256 esperado en las notas de la versión |
| Cable USB de **datos** (no solo de carga) | Instalación con `adb` (recomendada) |
| Un ordenador con `adb` (Platform-Tools de Google) | Instalar, comprobar y desinstalar |
| Opcional: otro launcher ya instalado (el de fábrica sirve) | Para volver a él antes de desinstalar |

---

## 1. Preparar el ordenador (adb)

1. Descarga **Android SDK Platform-Tools** (developer.android.com/tools/releases/platform-tools) y descomprímelo, o instálalo con tu gestor de paquetes.
2. Comprueba que funciona:
   ```bash
   adb version
   ```
3. Notas por sistema:
   - **Windows:** Windows 10/11 suele instalar el controlador ADB al conectar el teléfono con depuración activada. Si `adb devices` no lo ve, instala el controlador USB del fabricante (Xiaomi: *Mi USB Driver*; Honor: el controlador de *HONOR Suite / HiSuite* o *HONOR USB Driver*) y prueba otro cable/puerto.
   - **macOS:** no hace falta controlador.
   - **Linux:** si `adb devices` muestra `no permissions`, añade una regla *udev* para el fabricante o ejecuta `adb kill-server && sudo adb start-server`.

---

## 2. Preparar el teléfono

### 2.1 Común a todos los teléfonos
1. **Activa las Opciones de desarrollador** (cada marca lo llama distinto, ver 2.2 y 2.3): toca 7 veces el número de compilación/versión.
2. Dentro de *Opciones de desarrollador*, activa **Depuración por USB**.
3. Conecta el cable. En el teléfono aparecerá **«¿Permitir depuración por USB?»**: marca *Permitir siempre desde este ordenador* y acepta. Si no aparece, desconecta y reconecta, y comprueba que el modo USB sea **Transferencia de archivos (MTP)**.
4. En el ordenador:
   ```bash
   adb devices
   ```
   Debe listar tu teléfono como `device`. Si dice `unauthorized`, acepta el aviso del teléfono; si no aparece, prueba otro cable o puerto.
5. Anota los datos del teléfono para el registro (sección 10):
   ```bash
   adb shell getprop ro.product.manufacturer
   adb shell getprop ro.product.model
   adb shell getprop ro.build.version.release        # versión de Android
   adb shell getprop ro.build.version.sdk            # nivel de API (debe ser 31 o más)
   adb shell getprop ro.build.display.id             # build / versión del sistema del fabricante
   ```

### 2.2 Redmi / Xiaomi (MIUI y HyperOS)
| Paso | Ruta habitual (varía según versión) |
| --- | --- |
| Opciones de desarrollador | *Ajustes → Acerca del teléfono* → toca 7 veces **«Versión de MIUI»** (HyperOS: **«Versión de OS»**). Aparece «Ahora eres desarrollador». Después: *Ajustes → Ajustes adicionales → Opciones de desarrollador* |
| Depuración USB | Activa **Depuración por USB** |
| **Instalar mediante USB** | En muchas versiones de MIUI/HyperOS hay que activar también **«Instalar mediante USB»** (*Install via USB*) en *Opciones de desarrollador*. **Suele exigir iniciar sesión con una cuenta Mi y tener SIM o datos móviles** la primera vez. Sin esto `adb install` falla con `INSTALL_FAILED_USER_RESTRICTED` (ver sección 9) |
| Sin cuenta Mi | Si no quieres usar una cuenta, instala desde el teléfono (sección 4.B) en lugar de `adb install` |
| Opcional | **«Depuración USB (ajustes de seguridad)»**: solo hace falta si vas a simular toques desde el ordenador; **no** es necesaria para instalar |

### 2.3 Honor (MagicOS)
| Paso | Ruta habitual (varía según versión) |
| --- | --- |
| Opciones de desarrollador | *Ajustes → Acerca del teléfono* → toca 7 veces **«Número de compilación»**. Después: *Ajustes → Sistema y actualizaciones → Opciones de desarrollador* |
| Depuración USB | Activa **Depuración por USB**. Si el teléfono solo está cargando, activa también **«Permitir la depuración ADB en modo de solo carga»** |
| Instalación segura | Algunas versiones tienen **«Modo puro»** (*Ajustes → Seguridad → Modo puro* / *Pure mode*): bloquea instalar fuera de la tienda de la marca. **Desactívalo** para la prueba |
| Verificar apps por USB | Si aparece **«Verificar apps por USB»** en Opciones de desarrollador, puedes desactivarlo para evitar bloqueos de verificación (opcional) |
| Controlador en Windows | Si el PC no reconoce el teléfono, instala **HONOR Suite / HiSuite** o el controlador USB de Honor |

---

## 3. Comprobar el APK antes de instalarlo

En el ordenador, comprueba que el archivo no se corrompió:
```bash
shasum -a 256 focus-launcher-0.1.0-test.apk        # macOS/Linux
certutil -hashfile focus-launcher-0.1.0-test.apk SHA256   # Windows (cmd)
```
Compara con el SHA-256 de `docs/RELEASE-NOTES.md`. **El hash depende de la clave de depuración de la máquina que generó el APK**: si lo generaste tú, usa el hash que imprimió `build-test-apk.sh` (archivo `.sha256`).

---

## 4. Instalar

### A) Con adb (recomendado)
```bash
adb install -r focus-launcher-0.1.0-test.apk
```
Debe terminar en `Success`. Después, ábrelo:
```bash
adb shell am start -n com.focuslauncher/.MainActivity
```
O busca **«Focus Launcher»** en el cajón de apps del teléfono (con tu icono).

Si el teléfono muestra un aviso de seguridad durante la instalación (**Play Protect**, **Gestor del teléfono / Seguridad de MIUI**), elige **«Instalar de todos modos» / «Continuar»**. Es esperado: el APK tiene firma de depuración. Si falla, ve a la sección 9.

### B) Sin ordenador (copiando el APK al teléfono)
1. Copia el APK al teléfono (cable, tarjeta, o un enlace de descarga que controles). **No lo envíes por servicios que lo escaneen o modifiquen.**
2. Ábrelo desde el administrador de archivos del teléfono.
3. Android pedirá permiso para **instalar apps desconocidas desde esa app** (por ejemplo, *Archivos*). Actívalo:
   - **Redmi/Xiaomi:** *Ajustes → Privacidad / Apps → Permisos especiales → Instalar apps desconocidas* (HyperOS: puede estar en *Ajustes → Apps → Permisos → Instalar apps desconocidas*).
   - **Honor:** *Ajustes → Seguridad → Más ajustes de seguridad → Instalar apps de fuentes externas*.
4. Pulsa **Instalar**. Acepta los avisos de Play Protect / del gestor de seguridad con **«Instalar de todos modos»**.
5. Al terminar, **desactiva** el permiso de instalar apps desconocidas que acabas de dar a esa app.

---

## 5. Primer arranque y elegir Focus como pantalla de inicio

1. Al abrir Focus verás el **aviso de alcance** (léelo). Elige:
   - **«Entendido: usar Focus como pantalla de inicio»** → el sistema muestra su diálogo para elegir la app de inicio predeterminada; selecciona **Focus Launcher** y confirma; o
   - **«Entendido: probar Focus sin cambiar mi inicio»** → pruébalo antes y actívalo luego con el botón «Usar Focus como pantalla de inicio» de Inicio.
2. Si el sistema **no muestra el diálogo** (ocurre en algunas capas de fabricante), usa la ruta manual:
   - **Redmi/Xiaomi:** *Ajustes → Pantalla de inicio → Lanzador predeterminado* (o *Ajustes → Apps → Apps predeterminadas → App de inicio*). Elige **Focus Launcher**.
   - **Honor:** *Ajustes → Apps → Apps predeterminadas → Pantalla de inicio / App de inicio*. Elige **Focus Launcher**.
   - Alternativa dentro de Focus: **Ayuda → «Abrir ajustes de pantalla de inicio»**.
3. Comprueba que funciona: pulsa el botón/gesto de **Inicio**. Debe abrirse Focus. Desde el ordenador:
   ```bash
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```
   Debe devolver `com.focuslauncher/.MainActivity`.

---

## 6. Ajustes recomendados del fabricante (evitan que el sistema «mate» al launcher)

Los fabricantes limitan los procesos en segundo plano con agresividad. Si Focus se reinicia o parece perder la pantalla, revisa:

### Redmi / Xiaomi
- *Ajustes → Apps → Administrar apps → Focus Launcher → **Ahorro de batería** → **Sin restricciones*** (en HyperOS: *Ajustes → Batería → Ahorro de batería de apps*).
- *Seguridad (app) → Permisos → **Inicio automático*** → activa Focus Launcher.
- **Gestos y navegación:** *Ajustes → Pantalla de inicio → Navegación del sistema*. En algunas versiones de MIUI los **gestos de pantalla completa** y la vista de **Recientes** dependen del launcher del sistema; con un launcher de terceros pueden cambiar o mostrar los **3 botones**. Anota lo que veas (sección 10).

### Honor
- *Ajustes → Batería → **Inicio de apps*** (*App launch*) → Focus Launcher → desactiva el modo automático y activa **Inicio automático**, **Inicio secundario** y **Ejecutar en segundo plano**.
- *Ajustes → Batería → Optimización de batería* → Focus Launcher → **No optimizar**.
- **Navegación:** *Ajustes → Sistema y actualizaciones → Navegación del sistema* (gestos / tres botones). Prueba ambos si vas a validar el comportamiento de Recientes.

---

## 7. Verificación rápida después de instalar (5 minutos)

Marca cada paso en el teléfono. Cualquier fallo, anótalo con el modelo y la versión (sección 10).

- [ ] Se ve el **aviso de alcance** al primer arranque y el icono es el de Focus.
- [ ] Tras aceptar, **Inicio** muestra hasta 8 apps con su icono y «Todas las aplicaciones», «Buscar», «Configuración de Focus» y «Ayuda».
- [ ] El botón/gesto **Inicio** abre Focus (si lo elegiste como Home).
- [ ] **Configuración de Focus** → oculta una app (por ejemplo *Calculadora*). Desaparece de Inicio y de la búsqueda.
- [ ] **Seguridad → Activar PIN** (6 dígitos). Sale el indicador «Guardando el PIN…» y Configuración pide el PIN.
- [ ] Un PIN incorrecto dice «Quedan N intentos…».
- [ ] **Reinicia el teléfono**: Focus sigue siendo Inicio, la app sigue oculta y Configuración sigue pidiendo el PIN.
- [ ] **Ayuda** abre y muestra «El PIN olvidado no se puede recuperar» y los botones a Ajustes de Android.
- [ ] Gira la pantalla y sube la fuente al máximo (*Ajustes → Pantalla → Tamaño de fuente*): ninguna acción queda recortada.

---

## 8. Desinstalar por completo

El orden recomendado evita quedarte sin pantalla de inicio. Ten a mano otro launcher (el de fábrica de Honor/Xiaomi siempre existe).

### Paso 1 — Vuelve al launcher anterior
Cualquiera de estas vías:
- En Focus: **Ayuda → «Abrir ajustes de pantalla de inicio»** y elige el launcher de fábrica; o
- **Redmi/Xiaomi:** *Ajustes → Pantalla de inicio → Lanzador predeterminado* (o *Apps → Apps predeterminadas → App de inicio*) → **Launcher del sistema**; **Honor:** *Ajustes → Apps → Apps predeterminadas → Pantalla de inicio* → **Launcher del sistema** (en Honor suele llamarse *Pantalla de inicio* / *Magic Launcher*); o
- Desde el ordenador (Android 12+): `adb shell cmd role add-role-holder --user 0 android.app.role.HOME <paquete-del-launcher-de-fábrica>`.

Comprueba que el Home ya no es Focus:
```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```
(no debe devolver `com.focuslauncher`).

### Paso 2 (opcional, recomendado) — Borra los datos de Focus
Elimina reglas, PIN, aviso y la clave cifrada. **No** es obligatorio (al desinstalar se borran igual), pero sirve para comprobar el borrado:
- **Ajustes → Apps → *Focus Launcher* → Almacenamiento → Borrar datos** (Xiaomi: *Borrar datos → Borrar todos los datos*; Honor: *Almacenamiento → Borrar datos*), o
- `adb shell pm clear com.focuslauncher`

### Paso 3 — Desinstala
Elige una vía:
- **Desde el teléfono:** mantén pulsado el icono de *Focus Launcher* → **Desinstalar**; o *Ajustes → Apps → Administrar apps (Xiaomi) / Apps (Honor) → Focus Launcher → **Desinstalar***.
- **Con adb:**
  ```bash
  adb uninstall com.focuslauncher
  ```
  Debe responder `Success`. Si el teléfono tiene un perfil secundario o de trabajo, desinstala también ahí (Focus solo gestiona el perfil personal): `adb shell pm list users` y `adb uninstall --user <id> com.focuslauncher`.

### Paso 4 — Comprueba que no queda nada
```bash
adb shell pm list packages | grep focuslauncher          # no debe devolver nada (Windows: ... | findstr focuslauncher)
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME   # el launcher de fábrica
```
La clave de cifrado de Focus (Android Keystore) y su almacenamiento privado se **eliminan con la app**; no quedan datos de Focus en el teléfono. Si además instalaste el fixture de mediciones, quítalo: `adb uninstall com.focuslauncher.fixture200`.

### Paso 5 — Deja el teléfono como estaba
- Desactiva **Depuración por USB** y, si lo activaste, **Instalar mediante USB**.
- *Opciones de desarrollador → **Revocar autorizaciones de depuración USB*** (así ese ordenador deja de estar autorizado).
- Desactiva el permiso de **instalar apps desconocidas** que hayas dado al administrador de archivos.
- Si cambiaste ajustes de batería o inicio automático para Focus, revierte los que no te interesen.

---

## 9. Problemas frecuentes

| Síntoma / mensaje | Causa probable | Qué hacer |
| --- | --- | --- |
| `adb devices` no lista el teléfono / `unauthorized` | Depuración USB sin autorizar, cable solo de carga o falta de controlador | Cable de datos, modo MTP, acepta el aviso del teléfono, instala el controlador del fabricante, reinicia `adb kill-server` |
| `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` (Xiaomi) | Falta **Instalar mediante USB** o hay un aviso sin aceptar en el teléfono | Activa *Instalar mediante USB* (puede exigir cuenta Mi), acepta el aviso, o instala desde el teléfono (4.B) |
| `INSTALL_FAILED_VERIFICATION_FAILURE` / «App bloqueada por Play Protect» | Verificación de seguridad del sistema | Elige *Instalar de todos modos*; en Honor, desactiva el *Modo puro*; **solo como último recurso** y únicamente en el teléfono de pruebas, desactiva temporalmente el análisis de Play Protect (Play Store → perfil → Play Protect → ajustes) y vuélvelo a activar al terminar |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Ya hay un Focus instalado **firmado con otra clave** (otro ordenador) | Desinstala primero (`adb uninstall com.focuslauncher`) y vuelve a instalar |
| `INSTALL_FAILED_OLDER_SDK` | Android anterior a 12 | Este teléfono no es compatible; usa otro con Android 12+ |
| «Aplicación no instalada» al instalar desde el teléfono | Archivo dañado o permiso de fuentes desconocidas sin dar | Comprueba el hash (sección 3) y concede el permiso a la app desde la que abres el APK |
| El botón Inicio abre el launcher de fábrica | Focus no es el Home predeterminado | Repite la sección 5.2 |
| Focus «se reinicia» solo / se pierde al cambiar de app | Ahorro de batería agresivo del fabricante | Sección 6 |
| El diálogo para elegir Home no aparece | Capa del fabricante | Ruta manual (sección 5.2) |
| Los gestos de pantalla completa o Recientes cambian con Focus | El launcher del sistema integra esos gestos | Usa 3 botones o anota el comportamiento (sección 10) |
| El PIN se pide siempre al abrir Configuración | Es el comportamiento esperado (la sesión dura como máximo 2 min) | — |
| Me bloqueé con un PIN olvidado | El PIN no se recupera (por diseño) | Ayuda → cambiar de launcher → **Borrar datos** o desinstalar (sección 8) |
| No puedo desinstalar porque es el launcher predeterminado | Algunas capas lo impiden mientras es Home | Haz primero el Paso 1 de la sección 8 |

---

## 10. Pruebas pendientes en hardware real y plantilla de registro

La versión 0.1.0 está validada en **emuladores**. Estas comprobaciones solo se pueden cerrar en un teléfono físico. Rellena una fila por teléfono y entrégala junto con capturas (sin datos personales) y el modelo exacto.

### Qué comprobar
1. **TalkBack real:** *Ajustes → Accesibilidad → TalkBack*. Recorre: Bienvenida, Inicio, cajón (búsqueda), Configuración (interruptores: se anuncia «Visible/Oculta» **una sola vez**), puerta de PIN, Ayuda. Los encabezados deben anunciarse y el orden de foco ser lógico.
2. **Miniatura de Recientes:** abre *Configuración de Focus* (o la pantalla del PIN) y ve a **Recientes**: la tarjeta de Focus debe verse **en blanco** o genérica, sin nombres de apps ocultas. Intenta también una captura de pantalla estando en Configuración: debe bloquearse o salir en negro.
3. **Rendimiento (RNF-04):** arranque en frío con muchas apps. Con el APK release instalado y accesible por `adb`:
   ```bash
   adb shell am force-stop com.focuslauncher; adb logcat -c
   adb shell am start -W -n com.focuslauncher/.MainActivity
   adb logcat -d -s ActivityTaskManager | grep "Fully drawn com.focuslauncher"   # tiempo hasta lista filtrada
   ```
   Repite 20 veces (objetivo: p95 ≤ 2 s). La derivación del PIN se nota al activar o verificar el PIN (indicador a partir de 300 ms).
4. **Gestos / 3 botones y rotación**, con fuente al 200 %.
5. **Reinicio** (sección 7) y **«PIN olvidado»** completo (README §6) en ese teléfono.
6. **Transferencia de datos (D2D):** si tienes dos teléfonos, *Clonar teléfono / Mi Mover / Honor Clone* NO debe llevar los datos de Focus (reglas y PIN) al otro teléfono.

### Plantilla de registro
| Campo | Valor |
| --- | --- |
| Fabricante / modelo | |
| Versión de Android / API | |
| Build y versión del sistema (MagicOS / MIUI / HyperOS) | |
| RAM / almacenamiento | |
| Navegación (gestos / 3 botones) | |
| Instalación (adb / teléfono) y problemas | |
| Selección de Home (diálogo / ruta manual) | |
| Verificación rápida (sección 7): pasos que fallan | |
| TalkBack | |
| Miniatura de Recientes y captura de pantalla | |
| Arranque en frío p50 / p95 (n = 20) | |
| Reinicio y PIN olvidado | |
| Diferencias de menús respecto a esta guía | |
| Desinstalación completa (sección 8): resultado | |

Resultados por entorno ya obtenidos en emuladores: `docs/validation/T10.md`.
