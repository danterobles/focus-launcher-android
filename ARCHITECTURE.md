# Focus Launcher — Arquitectura del MVP

Versión: 1.0 · Fecha: 2026-11-03 · Alcance normativo: [PRD.md](PRD.md).

## 1. Decisiones y dependencias

Aplicación Android nativa, Kotlin, `minSdk = 31`, Compose/Material 3, MVVM con StateFlow, Hilt, Coroutines y Clean ligera. Un módulo Gradle `:app`, un proceso y una Activity. Se separan paquetes y contratos, sin crear un módulo Gradle por capa ni interfaces sin propósito de prueba.

T01 fija una combinación estable de SDK, AGP, Gradle, JDK, Kotlin, plugin Compose, Compose BOM, Lifecycle, Hilt y Protobuf/DataStore. Registrar números y fuentes oficiales en el repositorio; usar catálogo de versiones y wrapper. No usar versiones dinámicas ni fijar aquí versiones supuestamente actuales. La aplicación funciona offline; descargar herramientas y dependencias durante el desarrollo es una operación distinta.

| Decisión | Motivo / coste |
| --- | --- |
| Lista de paquetes ocultos | Ajuste visual simple; apps nuevas visibles por defecto. No es una whitelist. |
| Identidad de actividad separada de política | Varias entradas por paquete sin posibilidad de ocultar solo una y filtrar otra accidentalmente. |
| Solo perfil de ejecución | Evita soporte parcial de perfiles administrados y privados. |
| DataStore tipado único | Consistencia de PIN, contador y política en un estado persistido. |
| Serializer cifrado + Keystore | Protege confidencialidad e integridad del archivo local; exige tratar pérdida de clave como recuperación. |
| Sesión administrativa solo en memoria | No revive una autorización desde navegación o estado guardado. |
| UI sin lista completa hasta cargar política | Evita destellos de apps ocultas durante arranque/restauración. |

## 2. Organización propuesta

```text
app/src/main/
├── AndroidManifest.xml
├── proto/focus_state.proto
└── java/com/focuslauncher/
    ├── FocusLauncherApp.kt
    ├── MainActivity.kt
    ├── core/
    │   ├── di/              # Hilt, dispatchers, DataStore singleton
    │   ├── security/        # PinHasher, StateCipher, AdminSessionManager
    │   └── ui/              # tema, componentes accesibles
    ├── domain/
    │   ├── model/           # AppId, AppPackageKey, InstalledApp, estados
    │   ├── repository/      # catálogo, estado y autenticación
    │   └── usecase/         # observar visibles, abrir app, mutaciones protegidas
    ├── data/
    │   ├── android/         # LauncherApps, PackageManager, UserManager
    │   ├── local/           # Protobuf, serializer cifrado, migraciones
    │   └── repository/      # implementaciones, reconciliación
    └── presentation/
        ├── onboarding/
        ├── home/
        ├── drawer/
        ├── settings/
        └── security/
```

Dominio contiene modelos Kotlin sin `Context`, `UserHandle`, Drawable ni APIs Compose. En data se convierte `ComponentName` a cadenas y `UserHandle` a serial persistible. Presentation depende de dominio; data implementa sus contratos. Hilt une implementaciones en el punto de composición, sin localizar servicios desde Composables.

Flujo de dependencias: `Compose → ViewModel → casos de uso → contratos`; `data → contratos`; `Hilt → composición`. Flujo de estado: Android/DataStore → repositorios → casos de uso → StateFlow → Compose.

## 3. Responsabilidades

| Componente | Responsabilidad |
| --- | --- |
| FocusLauncherApp | Inicializar Hilt y dependencias de alcance aplicación. |
| MainActivity | Entrada Home/launcher, lifecycle, navegación, resultados del rol, invalidación administrativa. |
| HomeRoleController | Consultar disponibilidad/tenencia de `ROLE_HOME`; crear solicitud. La Activity la lanza con Activity Result. |
| AndroidAppCatalogRepository | Descubrir actividades del perfil de ejecución, observar cambios, ordenar y resolver lanzamientos. |
| FocusStateRepository | Observar estado válido y ejecutar transformaciones DataStore; sin exponer verificador de PIN a UI. |
| ObserveVisibleApps | Combinar catálogo y política válida, filtrar por paquete/serial, ordenar y producir entradas de presentación. |
| LaunchApp | Revalidar identidad, política y disponibilidad justo antes de delegar al sistema. |
| AdminAccessCoordinator | Serializar autenticación y mutaciones administrativas con Mutex; verificar sesión en cada escritura. |
| PinHasher / StateCipher | Derivación/comparación del PIN y cifrado autenticado; sin lógica de navegación. |
| ViewModels | Reducir eventos a estado inmutable, ejecutar casos de uso y exponer StateFlow. |

## 4. Integración Home y manifiesto

Esqueleto orientativo; el proyecto debe agregar tema, recursos y reglas de extracción verificadas:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>
    <application
        android:name=".FocusLauncherApp"
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:dataExtractionRules="@xml/data_extraction_rules">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

La entrada LAUNCHER permite abrir Focus antes de elegirlo como Home. El propio paquete se excluye del catálogo; la configuración pertenece al chrome permanente de Focus.

Solicitar rol únicamente por acción del usuario: comprobar `isRoleAvailable(ROLE_HOME)` y `isRoleHeld(ROLE_HOME)`; si procede crear `createRequestRoleIntent(ROLE_HOME)` y lanzar. Consultar de nuevo al resultado y en `onResume`; no asumir éxito por el resultado aislado. Si no está disponible, mostrar explicación y ofrecer ajustes Home, con captura de ausencia de Activity. No escribir ajustes del sistema para forzar Home. [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager).

`singleTask` evita múltiples instancias Home, pero no autoriza administración. `onNewIntent` para Home navega a Home y revoca sesión. Ignorar extras externos que intenten seleccionar paquetes, cambiar reglas o abrir ajustes autenticados. No hay deep links administrativos, receivers de comandos ni servicios exportados.

No se requiere `BOOT_COMPLETED`: Android resuelve su Home y Focus carga el estado al abrirse tras desbloquear. No hay soporte Direct Boot ni datos en almacenamiento accesible antes del desbloqueo.

## 5. Catálogo y ejecución

1. Obtener `Process.myUserHandle()` y su serial con UserManager. Si no puede obtenerse un serial válido, no usar un serial por defecto; emitir error de catálogo.
2. Consultar `LauncherApps.getActivityList(null, currentUser)` fuera del hilo principal.
3. Intersectar por componente con `PackageManager.queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER, flags = 0)` del usuario actual. Es una decisión del MVP para conservar actividades launcher reales y excluir entradas sintetizadas que apuntan a detalles de una app.
4. Eliminar componentes inhabilitados/no exportados/no accesibles, el propio paquete y duplicados exactos por `AppId`. Conservar dos componentes distintos del mismo paquete.
5. Ordenar por etiqueta con Collator de la configuración regional, desempatar por paquete y componente. Mostrar etiqueta de actividad; si no existe, etiqueta de app y después paquete. Icono de reserva si falla.
6. Abrir con `LauncherApps.startMainActivity(component, currentUser, sourceBounds, options)`. No usar `getLaunchIntentForPackage()` como sustituto de la identidad seleccionada.

LauncherApps enumera entradas por usuario y puede incluir entradas sintetizadas. El adaptador debe tratar excepciones de permiso o desaparición y jamás deducir de un resultado vacío que se controlan todas las apps del teléfono. [Referencia LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps).

Registrar `LauncherApps.Callback` mientras existe un consumidor del catálogo; agrupar eventos y reconsultar. Liberar callback al cancelar la observación. Refrescar también al reanudar para cubrir cambios perdidos. `onPackageRemoved` durante actualización no borra reglas. Cambios de locale invalidan etiquetas/orden; cambios de configuración o paquetes invalidan iconos. Una caché LRU en memoria usa AppId, densidad y versión/configuración; no guardar iconos en DataStore.

Antes de abrir, `LaunchApp` vuelve a consultar la política válida y verifica que la identidad siga en catálogo disponible. Si una ocultación concurrente ya se confirmó, se rechaza el toque obsoleto. Capturar `SecurityException`, `ActivityNotFoundException` y estado de usuario no disponible; mostrar mensaje genérico y refrescar. Focus no puede impedir que una app se abra externamente.

## 6. Visibilidad de paquetes y perfiles

`<queries>` MAIN/LAUNCHER es acotado a las consultas auxiliares de este catálogo; no solicita permiso runtime. No se usa `QUERY_ALL_PACKAGES`, `MATCH_UNINSTALLED_PACKAGES` ni inventario global. APIs de PackageManager y LauncherApps tienen contratos distintos: no suponer que un resultado de una habilita automáticamente todas las consultas de la otra. La ausencia de resultados requiere diagnóstico/prueba, nunca añadir permisos amplios como arreglo automático. [Declaración de visibilidad](https://developer.android.com/training/package-visibility/declaring).

No iterar `LauncherApps.getProfiles()` para poblar el cajón. Solo perfil de ejecución, validado inicialmente como perfil personal del propietario. El modelo retiene serial para evitar colisiones y preparar una futura ampliación; no habilita por sí mismo soporte multiperfil. No reutilizar `UserHandle.hashCode()`, `identifier` o un índice de lista como serial persistente.

Espacio privado (Android 15+) queda fuera. No declarar `ACCESS_HIDDEN_PROFILES`. Soportarlo posteriormente requerirá rol Home, permiso y una experiencia separada con ocultación, bloqueo/desbloqueo y ausencia de resultados mientras está bloqueado; no basta con iterar perfiles. No se intentará desbloquearlo con el PIN de Focus. [Requisitos oficiales de Espacio privado](https://developer.android.com/about/versions/15/behavior-changes-all#private-space).

## 7. Estado de UI y concurrencia

Modelar de forma explícita `Loading`, `Ready`, `Empty`, `ReadError` y `RecoveryRequired`; catálogo y persistencia tienen errores separados. La combinación no emite lista de apps hasta que haya una política válida. Un valor inicial `emptySet()` de ocultos no representa una lectura pendiente.

`HomeUiState` contiene entradas ya visibles; Home toma 8 y el cajón usa la lista completa. La búsqueda vive solo en memoria y deriva de esa lista, nunca del catálogo administrativo completo. Al ocultar, una sola emisión actualiza todas las superficies.

`SettingsUiState` puede mostrar paquetes ocultos solo mientras está autorizada la administración. Al revocar sesión se vacía ese estado y se vuelve al PIN/Home. `SavedStateHandle` nunca contiene autorización, PIN ni lista administrativa. No precargar ajustes protegidos al restaurar back stack.

Usar `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)` y `collectAsStateWithLifecycle`. Las operaciones de archivo/Binder/iconos usan dispatcher IO; KDF usa Default con límite de concurrencia. No capturar `CancellationException` como error genérico. Eventos de navegación/resultados del rol son de una sola entrega; el estado de guardado vive en StateFlow y no depende de recibir un toast.

## 8. Administración, PIN y errores

El PIN opcional y sus parámetros se definen solo en [DATA.md](DATA.md). No se almacena el PIN original. Keystore protege la clave de cifrado del estado, no almacena/compara directamente el PIN.

El coordinador mantiene sesión opaca en memoria y reloj monotónico, válida por máximo 120 s. Se revoca al salir de administración, recibir Home, `onStop` salvo cambio de configuración, pantalla apagada/bloqueada o muerte del proceso. En reanudación comprobar plazo y bloqueo del dispositivo antes de renderizar administración. Rotar no renueva ni persiste la autorización. La observación del estado de pantalla es dinámica y no exporta un receiver de comandos.

La revocación de sesión y el cambio de generación de autenticación son inmediatos en memoria: no esperan a que termine la KDF ni a adquirir el Mutex de escrituras. Si se sale de la pantalla o se bloquea el dispositivo durante una verificación, su resultado tardío no puede abrir administración. Validar la generación, pantalla activa y estado del dispositivo al emitir una sesión.

Cada mutación de visibilidad, PIN o restablecimiento atraviesa el coordinador. Con PIN deshabilitado puede autorizar sin desafío; habilitarlo lo deja bloqueado al terminar. Deshabilitarlo, cambiarlo y restablecer exigen sesión vigente si ya estaba habilitado. El Mutex serializa comprobación, escritura y cambios de credencial; revalidar autorización después de trabajo costoso y antes de confirmar.

Pantallas administrativas y de PIN usan protección de captura `FLAG_SECURE` y no deben filtrar etiquetas de apps ocultas a miniaturas de Recientes; conservar la protección al pasar a segundo plano hasta reemplazar el contenido sensible. El PIN se oculta visualmente, no se copia ni persiste; buffers temporales se limpian cuando la API lo permita. Esto no equivale a protección frente a software con privilegios del sistema.

No instalar `ReplaceFileCorruptionHandler` que convierta un fallo en ajustes vacíos. Si hay última política válida durante el mismo proceso se puede conservar solo la vista ya filtrada con aviso y escritura deshabilitada; de lo contrario mostrar Home sin apps, ayuda y salida a Android. Una clave perdida, etiqueta GCM inválida o schema futuro exige `RecoveryRequired`. No desactivar el PIN automáticamente. [DataStore](https://developer.android.com/topic/libraries/architecture/datastore).

## 9. Estrategia de comprobación

Pruebas unitarias: filtrado por paquete/serial, duplicados, búsqueda, carreras de lanzamiento, sesión, contador, cancelación y errores. Instrumentación: serializer cifrado con Keystore real, DataStore y reinicio del proceso. Compose: bienvenida, estados vacíos, ocultación, autenticación y navegación restaurada. Validación de sistema: rol Home real, Recientes, reinicio del teléfono y OEM.

[TASKS.md](TASKS.md) contiene la secuencia y matriz obligatorias. No se considera probada la selección Home mediante un mock de RoleManager ni la seguridad del PIN con pruebas exclusivamente visuales.

## 10. Notas de implementación (T11)

Aclaraciones y adiciones surgidas al implementar. **Ningún contrato cambia de significado**; cada punto está validado en `docs/validation/`.

- **Manifiesto final.** El esqueleto de §4 es orientativo. Además: `android:icon`/`roundIcon` (icono adaptable con capa monocroma), `dataExtractionRules` con 18 exclusiones, y `<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" tools:node="remove" />` porque esa dependencia aportaba un receiver **exportado**. La tarea `./gradlew :app:verifyReleaseManifest` audita el manifiesto **combinado** del release (permisos, exports, backups, `queries`) y se ejecuta tras `assembleRelease`.
- **Destinos de navegación:** `Welcome`, `Home`, `Drawer`, `Help`, `Settings` y `Security` (los dos últimos administrativos). El estado vive solo en memoria; tras morir el proceso siempre se arranca en Inicio. Salir del área administrativa, recibir Home, `onStop` (salvo cambio de configuración) y `ACTION_SCREEN_OFF` revocan la sesión.
- **Aviso de alcance (RF-01):** la Bienvenida reemplaza a Inicio/cajón/Configuración mientras la configuración sea legible y `onboarding_accepted` sea `false` (instalación nueva o tras restablecer); solo la Ayuda la precede. Mientras la configuración carga o falla no se inventa una Bienvenida.
- **Tema del sistema:** `FocusTheme` (claro/oscuro del dispositivo y colores dinámicos; siempre disponibles con `minSdk 31`) y tema de ventana con variante `values-night`.
- **Diseños con poco alto:** con alto disponible < 520 dp (horizontal, fuente grande, teclado) Inicio y cajón son una sola cuadrícula desplazable y Configuración una sola lista; con alto suficiente se conserva la barra fija de accesos.
- **Búsqueda:** `SearchIndex` normaliza los nombres **una vez por lista** y cada pulsación solo normaliza la consulta (RNF-04).
- **Arranque medible:** `reportFullyDrawn()` se llama cuando Inicio muestra su primer contenido útil (lista, vacío o bloqueo explícito).
- **Compilaciones:** `debug`, `release` (R8) y `benchmark` (como release, no depurable, **sin R8** y firmada con la clave de depuración local; solo con `-PperfBuild=true`, para medir RNF-04 con pruebas instrumentadas). `scripts/build-test-apk.sh` genera el APK de prueba.
- **Estado de la UI de administración:** `AdminGate` (`Loading`, `Blocked`, `NeedsPin`, `Open`) decide qué se compone; los ViewModels administrativos usan `replayExpiration = 0` para no conservar etiquetas ni estado de PIN sin observadores.
- **Pruebas:** la accesibilidad automática (ATF) solo es efectiva en API 34+ (se omite antes); las pruebas instrumentadas usan un almacén aislado por prueba (alias y archivo únicos).
