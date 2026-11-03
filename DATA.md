# Focus Launcher — Modelos, persistencia y PIN

Versión: 1.0 · Fecha: 2026-11-03 · Contrato del MVP de [PRD.md](PRD.md) y [ARCHITECTURE.md](ARCHITECTURE.md).

## 1. Principios y fuente de verdad

- Android es la fuente de verdad de actividades disponibles, etiquetas e iconos; su catálogo se mantiene en memoria.
- Un DataStore tipado, con Protobuf y serializer cifrado, es la fuente persistida de reglas, bienvenida y seguridad. No usar Room ni SharedPreferences en paralelo.
- Un solo archivo y una sola instancia Hilt `@Singleton` en un proceso. Toda transformación usa `updateData`; no hacer read-modify-write sobre copias de UI. [Reglas de DataStore](https://developer.android.com/topic/libraries/architecture/datastore).
- La política es una lista de paquetes ocultos. Ausencia de una regla significa visible, únicamente cuando la lectura del estado terminó correctamente.
- Ningún estado de error, pendiente o incompatible equivale a lista vacía de ocultos ni a PIN deshabilitado.

## 2. Identidades y modelos de dominio

Los siguientes modelos son contratos orientativos Kotlin; no constituyen código completo listo para compilar. Protobuf genera otros tipos internos; los mappers no deben exponerlos a la UI.

```kotlin
@JvmInline
value class UserSerial(val value: Long)

data class AppId(
    val packageName: String,
    val activityName: String, // nombre completo de clase, no etiqueta traducida
    val userSerial: UserSerial,
)

data class AppPackageKey(
    val packageName: String,
    val userSerial: UserSerial,
)

data class InstalledApp(
    val id: AppId,
    val label: String,
    val available: Boolean,
)

data class VisibilityPolicy(
    val hiddenPackages: Set<AppPackageKey>,
)

data class LauncherSettings(
    val onboardingAccepted: Boolean,
    val visibility: VisibilityPolicy,
    val pinEnabled: Boolean, // proyección de PinConfig, no un booleano independiente
)
```

AppId identifica una entrada del launcher; AppPackageKey identifica una regla de ocultación. Dos actividades del mismo paquete son dos entradas y comparten una sola regla. Dos instalaciones del mismo paquete en perfiles diferentes tendrían claves distintas; el MVP consulta únicamente el serial actual.

La capa Android convierte un `ComponentName` a paquete y clase completos, y obtiene el serial con `UserManager.getSerialNumberForUser()`. Un serial negativo/desconocido es error, no 0. Se resuelve de nuevo el handle al ejecutar y se exige que corresponda al perfil actual. El serial es local al dispositivo; no es un identificador global exportable.

No persistir `UserHandle`, Drawable, Bitmap, etiqueta, estado instalado, resultados de búsqueda o `isVisible` por actividad. `isVisible` se calcula para evitar dos fuentes de verdad.

## 3. Esquema Protobuf v1

Ruta propuesta: `app/src/main/proto/focus_state.proto`. Los nombres de paquete generado son internos a data.

```proto
syntax = "proto3";
package focuslauncher.storage;
option java_package = "com.focuslauncher.data.local.proto";
option java_multiple_files = true;

message PackageKeyProto {
  string package_name = 1;
  int64 user_serial = 2;
}

message PinVerifierProto {
  string kdf = 1;            // "PBKDF2WithHmacSHA256"
  uint32 iterations = 2;     // v1: 600000
  bytes salt = 3;            // v1: 16 bytes
  bytes verifier = 4;        // v1: 32 bytes
}

message PinConfigProto {
  oneof mode {
    bool disabled = 1;      // presencia + valor true; false es inválido
    PinVerifierProto enabled = 2;
  }
}

message AttemptStateProto {
  uint32 reserved_in_window = 1; // 0..5
  uint32 penalty_level = 2;      // 0..5, siguiente nivel si hubo espera
  bool cooldown_active = 3;
  uint64 cooldown_duration_ms = 4;
}

message FocusStateProto {
  uint32 schema_version = 1;     // exactamente 1 para esta implementación
  bool onboarding_accepted = 2;
  repeated PackageKeyProto hidden_packages = 3;
  PinConfigProto pin = 4;
  AttemptStateProto attempts = 5;
}
```

No persistir `pinEnabled` además de `pin.mode`. Un `oneof` ausente, `disabled=false`, verificador incompleto o algoritmo no reconocido es estado inválido. El Protobuf por defecto de todos ceros no es una configuración operativa válida.

Creación limpia v1: schema 1, bienvenida pendiente, cero reglas, `pin.disabled=true`, intentos a cero y sin espera. Se construye explícitamente en bootstrap para una instalación nueva, no desde un catch de lectura. No hace falta persistir columnas o tema: el MVP usa 4 columnas fijas y tema del sistema.

Invariantes al leer y antes de escribir:

1. Schema 1 y modo PIN válido; serial no negativo y paquete no vacío. Las claves duplicadas se normalizan como conjunto; nunca usar concatenación ambigua de paquete/serial.
2. PIN habilitado implica salt de 16 bytes, verificador de 32 bytes, KDF admitida e iteraciones 600000 en v1. Rechazar parámetros arbitrarios que puedan provocar una KDF desmesurada al leer un archivo inválido.
3. PIN deshabilitado implica intentos reiniciados; sesión siempre ausente después de arrancar.
4. Intentos entre 0 y 5, nivel entre 0 y 5. Espera activa implica 5 reservas y duración perteneciente a {30000, 60000, 120000, 240000, 480000, 900000}; sin espera, duración 0 y reservas como máximo 4.
5. La propia app no puede ser objeto de una regla nueva. Reglas heredadas que la mencionen no afectan el acceso permanente a ajustes.
6. Límite del archivo cifrado: 2 MiB y 10000 reglas para contener entradas corruptas; valores por encima producen error explícito sin truncar ni descartar reglas silenciosamente.

## 4. Archivo, cifrado y Keystore

Ubicación: `File(context.noBackupFilesDir, "focus/focus_state.pb.enc")`, mediante `DataStoreFactory` y `produceFile`. Crear directorio privado; no usar almacenamiento compartido, device-protected storage ni un segundo archivo de PIN.

Formato binario del sobre v1, independiente del schema interno:

```text
magic = 4 bytes ASCII "FCLS"
envelopeVersion = 1 byte (1)
ivLength = 1 byte (12)
iv = 12 bytes
ciphertextAndTag = AES-GCM(protobuf), con tag de 128 bits
```

Cabecera fija (magic + versión + longitud) y cadena de contexto `com.focuslauncher|focus-state|v1` se suministran como AAD. Prohibir IVs de longitud inesperada, archivos truncados y sobre/versiones desconocidos. Generar un IV nuevo por escritura desde Cipher/SecureRandom; nunca usar PIN, serial o contador como IV ni reutilizarlo.

Clave: AES de 256 bits en proveedor `AndroidKeyStore`, alias `focus_state_aes_v1`, propósitos ENCRYPT/DECRYPT, GCM/NoPadding y cifrado aleatorizado. Es una clave no exportable a través de la API; respaldo hardware depende del dispositivo, sin requerir StrongBox. No requiere autenticación biométrica/de dispositivo por uso: Home debe poder leer su política sin desafío del sistema después del desbloqueo normal. El PIN de Focus se comprueba en la aplicación. [Android Keystore](https://developer.android.com/privacy-and-security/keystore) y [criptografía Android](https://developer.android.com/privacy-and-security/cryptography).

Se cifra el estado completo, incluido el verificador derivado y el contador. **DataStore por sí solo no cifra. Keystore no guarda un PIN ni deriva automáticamente su hash.** No introducir EncryptedSharedPreferences como segundo almacén.

El serializer limita tamaño, valida cabecera, autentica/descifra y decodifica Protobuf. Un tag inválido no devuelve defaults. La clave no se regenera silenciosamente si existe un archivo cifrado. Tampoco se sobrescribe el archivo cuando la clave es temporalmente inaccesible.

Bootstrap antes de exponer repositorios operativos:

| Archivo / alias | Resultado |
| --- | --- |
| Ambos ausentes, directorio sin restos de estado | Instalación limpia; crear clave y persistir estado inicial. Un fallo deja error, no una Home con catálogo sin política. |
| Ambos presentes | Leer y validar; solo entonces emitir `Ready`. |
| Archivo presente, clave ausente/inválida | `RecoveryRequired`; conservar archivo, no crear otra clave para ignorarlo. |
| Archivo ausente, alias presente o restos temporales | `RecoveryRequired`; puede ser inicialización interrumpida. No asumir PIN deshabilitado. |
| Archivo ilegible, tag inválido o schema desconocido | Error/`RecoveryRequired` según causa; sin defaults automáticos. |

La creación de alias y archivo no es una transacción de Keystore. Una interrupción entre ambos puede requerir recuperación externa. Es una decisión conservadora del MVP. El bootstrap debe distinguir explícitamente creación inicial del `Serializer.defaultValue` que DataStore usa si no hay archivo.

Copia y restauración: `noBackupFilesDir` excluye el estado de los mecanismos normales de backup. Además declarar `allowBackup=false`, `fullBackupContent=false` y reglas `dataExtractionRules` que excluyan datos persistentes en `cloud-backup` y `device-transfer` (dominios root, file, database, sharedpref, external y variantes device aplicables). No implementar BackupAgent ni reglas de transferencia entre plataformas. Validar el manifiesto combinado y el comportamiento en el OEM objetivo: `allowBackup=false` aislado no garantiza evitar D2D en todos los fabricantes. [Auto Backup](https://developer.android.com/identity/data/autobackup).

## 5. PIN: creación, verificación y cambio

Política de producto: entre 6 y 12 caracteres `0`–`9`, sin normalización Unicode que cambie el secreto. Aceptar ceros iniciales; usar CharArray cuando sea posible, nunca Int. Confirmación idéntica al crear/cambiar; no guardar la longitud ni una pista del PIN.

KDF v1: `PBKDF2WithHmacSHA256`, 600000 iteraciones, salt aleatorio de 16 bytes por alta/cambio y salida de 256 bits. Usar SecretKeyFactory/PBEKeySpec de plataforma sin fijar un proveedor salvo para Keystore. Limpiar PBEKeySpec y buffers temporales; Compose/IME pueden crear copias inmutables, por lo que no se promete borrado perfecto de memoria. Comparar bytes con `MessageDigest.isEqual`.

600000 es el parámetro de diseño elegido a partir de la referencia PBKDF2-HMAC-SHA256 de [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Medir en dispositivo de referencia; no bajar silenciosamente el coste por rendimiento. PBKDF2 no aporta entropía a un PIN corto y no convierte el control visual en seguridad de sistema. El estado cifrado y la espera complementan la KDF.

Creación: sin PIN actual, validar y confirmar → derivar fuera de Main → comprobar que no cambió la versión/configuración observada → `updateData` con modo habilitado y contador limpio → revocar acceso administrativo y pedir el nuevo PIN al siguiente ingreso. No anunciar éxito antes de persistir.

Verificación: reservar intento duradero → derivar → comparar → persistir resultado → únicamente tras éxito duradero emitir sesión opaca. Si falla cualquier escritura, no abrir administración. Capturar una generación de autenticación al iniciar; salir de la pantalla, recibir Home o bloquear el dispositivo invalida esa generación inmediatamente, aunque la KDF siga en curso. Un resultado tardío puede contabilizar el intento, pero no emitir sesión ni navegar a ajustes. Nunca registrar entrada, salt/verificador, claves o bytes del estado descifrado.

Cambio/desactivación: exigir sesión vigente en el coordinador; revalidarla justo antes del commit. Cambio utiliza nuevo salt y verificador y revoca sesión tras guardar. Desactivación borra verificador/contador y revoca sesión; en adelante ajustes quedan abiertos porque no hay PIN habilitado. El usuario debe confirmarlo expresamente.

La sesión no depende de `pinEnabled` en UI. Cuando el PIN está habilitado, el caso de uso valida un token opaco expedido por el coordinador. No aceptar `isAdmin=true` como argumento ni un token reconstruible desde Intent/SavedState.

## 6. Intentos y espera: algoritmo normativo

La limitación es local y resistente a cierre/reinicio normal, no a quien borra datos o controla el sistema. Se usa un Mutex de alcance aplicación para serializar reservas, verificaciones y cambios administrativos. No ejecutar dos KDF en paralelo. La revocación en memoria no espera ese Mutex; una operación que reanuda debe comprobar la generación y autorización antes de confirmar una mutación o emitir sesión.

Estado inicial: reservas 0, nivel 0, espera inactiva. Duraciones por nivel: `[30, 60, 120, 240, 480, 900]` segundos.

1. Si hay espera activa, denegar un nuevo intento antes de ejecutar KDF. Dentro del mismo proceso calcular el vencimiento con `SystemClock.elapsedRealtime()`; nunca con hora de pared.
2. Antes de cada KDF, guardar atómicamente una reserva: sumar 1. Si llega a 5, marcar espera activa con la duración del nivel actual y subir el nivel hasta máximo 5. La quinta solicitud ya reservada puede completar su comprobación; la espera se aplica a las posteriores.
3. Solo si la reserva se guardó, derivar y comparar. Una cancelación o muerte de proceso consume esa reserva. Si el quinto intento está en curso, no permitir expirar su espera para admitir otro intento concurrente.
4. Si acierta y se completa el commit, reiniciar reservas/nivel/espera y abrir sesión. Acertar en el quinto intento también reinicia todo. Si falla, conservar la reserva; mostrar espera si corresponde.
5. Al vencer la espera y sin verificación en curso, persistir reservas 0, espera inactiva y duración 0, manteniendo el nivel. Solo entonces permitir la siguiente reserva. Un éxito posterior reinicia el nivel.
6. En un nuevo proceso, si el archivo dice espera activa, volver a esperar la duración completa indicada desde el nuevo `elapsedRealtime()`. No usar la hora del sistema para descontar tiempo mientras no hubo proceso. Esto puede prolongar la espera al reiniciar; se comunica al usuario y evita reducirla cambiando fecha o reiniciando el teléfono.

Si quedan 4 reservas sin espera al morir el proceso, la siguiente es la quinta. Si el proceso muere después de una reserva correcta pero antes del commit de éxito, esa reserva permanece; es una degradación de disponibilidad aceptada, sin autorización concedida. Una escritura fallida de reserva o vencimiento bloquea nuevos intentos hasta resolver el error de almacenamiento.

No persistir un contador descendente cada segundo. Persistir solo reservas, activación/fin de espera y éxito; la cuenta atrás visible se deriva del reloj monotónico. No guardar `System.currentTimeMillis()` como fuente de autorización. Tests con reloj e implementación KDF inyectables deben cubrir cancelación y reinicio.

## 7. Contratos de repositorio y autorización

API conceptual; cada implementación concreta puede ajustar nombres, manteniendo estas garantías:

```kotlin
interface AppCatalogRepository {
    val catalog: Flow<CatalogState>
    suspend fun refresh()
    suspend fun launch(id: AppId): LaunchResult
}

interface FocusStateRepository {
    val settings: Flow<SettingsReadState> // proyección sin verificador
    // Escrituras/crudo solo disponibles al coordinador de data/security.
}

interface AdminRepository {
    suspend fun verifyPin(pin: CharArray): AuthenticationResult
    fun lock()
    suspend fun setPackageHidden(key: AppPackageKey, hidden: Boolean): MutationResult
    suspend fun enablePin(pin: CharArray, confirmation: CharArray): MutationResult
    suspend fun changePin(pin: CharArray, confirmation: CharArray): MutationResult
    suspend fun disablePin(): MutationResult
    suspend fun resetConfiguration(): MutationResult
}
```

`AdminRepository` aplica internamente la sesión opaca; no significa que cualquier caller pueda escribir. Las operaciones mutadoras comprueban PIN vigente/política actual y error de lectura antes de hacer `updateData`. Con PIN deshabilitado se permiten esas operaciones sin desafío. Las implementaciones crudas de escritura son internas y no se inyectan en ViewModels.

Resultados explícitos: `Success`, `InvalidInput`, `IncorrectPin`, `Cooldown(remaining)`, `AuthenticationRequired`, `Unavailable`, `StorageFailure` y `RecoveryRequired`. `SettingsReadState` separa Loading, Ready, ReadError y RecoveryRequired; no representa fallos con null/defaults. `CatalogState` distingue catálogo vacío de error y permite refrescar.

`LaunchApp` es el caso de uso autorizado para abrir desde UI; verifica política válida antes de llamar al adaptador de catálogo. No exponer el adaptador Android como vía alternativa desde un Composable.

## 8. Reglas de reconciliación y borrado

| Evento | Catálogo | Política / seguridad |
| --- | --- | --- |
| Instalación nueva | Reconsultar; entradas visibles si no hay regla | No añadir regla automáticamente. |
| Actualización / alias nuevo | Reconsultar componentes e iconos | Mismo paquete/serial conserva ocultación de todas sus actividades. |
| Desinstalación / app deshabilitada | Quitar entradas no disponibles | Conservar regla como tombstone; una reinstalación del mismo paquete sigue oculta. |
| Otro perfil instala mismo paquete | Sin entradas nuevas en este MVP | No copiar reglas ni asumir equivalencia entre perfiles. |
| Cambio de locale | Rehacer etiquetas/orden/iconos pertinentes | Sin cambio de claves. |
| Restablecer dentro de Focus | Reconsultar tras commit | Con autorización si hay PIN y confirmación, escribir estado inicial v1 usando la clave actual, revocar sesión y volver a bienvenida. |
| Borrar datos/desinstalar desde Android | Nueva inicialización posterior | Se pierden políticas y PIN; no se puede impedir con este MVP. |

No depurar tombstones automáticamente. El MVP no migra datos entre dispositivos ni perfiles. Un paquete reinstalado con el mismo nombre queda oculto independientemente de su firmante; esta es una regla visual, no una certificación de identidad de la app.

## 9. Migraciones y recuperación

Sobre cifrado y schema tienen versiones independientes. Schema futuro no soportado: bloquear escritura y ofrecer ayuda/salida, sin sobrescribir campos. Cambios compatibles agregan números nuevos; no reutilizar ni renumerar campos eliminados (declararlos `reserved`). Cambios de significado exigen migración y pruebas con fixtures reales de la versión anterior.

v1 no importa ningún formato anterior de la conversación: sus modelos eran ilustrativos y no hubo una aplicación persistida. Las futuras migraciones deben ejecutarse antes de emitir Ready y no eliminar PIN ni reglas por defecto. No hacer downgrade automático.

Una corrupción, pérdida de clave o PIN olvidado no ofrece un botón sin autenticación para borrar las reglas dentro de Focus. Mostrar instrucciones para cambiar launcher y borrar datos/desinstalar desde Ajustes Android; esto es una vía externa que el propietario ya controla. Con estado válido y sesión vigente, el restablecimiento interno es explícito y confirmado. El error temporal de IO permite reintentar conservando datos.

## 10. Casos de prueba obligatorios

- Identidades: dos actividades de un paquete; mismo paquete con dos seriales; serial inválido; app sin etiqueta/icono; Focus excluido.
- Política: app nueva visible; ocultación de todas sus entradas; cambio con búsqueda activa; regla retenida durante actualización/desinstalación/reinstalación.
- Archivo: round-trip, nonce distinto por escritura, bit alterado, truncado, clave ausente, cabecera/schema futuro, tamaño excesivo y archivo ausente con alias existente.
- PIN: límites 5/6/12/13 dígitos, ceros iniciales, caracteres Unicode rechazados, confirmación distinta, salt nuevo y verificador nunca expuesto a UI.
- Intentos: cinco reservas, acierto en quinta, cancelación tras reserva, fallo de commit, escalado a 900 s, éxito reinicia nivel, reinicio reaplica espera y cambio de hora no la acorta.
- Sesión: plazo de 120 s, rotación, Home, segundo plano, pantalla apagada, muerte del proceso, verificación tardía tras salir de PIN y mutación con token revocado.
- Errores: nunca emitir todas las apps por defecto ni deshabilitar PIN ante excepción. No registrar secretos ni restaurarlos desde backup.

La implementación y evidencia se ordenan en [TASKS.md](TASKS.md); [CLAUDE.md](CLAUDE.md) prohíbe atajos que invaliden estos contratos.

## 11. Notas de implementación (T11)

Contratos finales tal como quedaron implementados. **No cambian los modelos, el esquema Protobuf v1, el sobre cifrado ni el algoritmo de intentos**; son adiciones de interfaz y aclaraciones.

- **`AdminRepository`** añade `isAuthorized: StateFlow<Boolean>` (sesión vigente; nunca persistida) y `cooldownRemainingMs()`. `verifyPin` devuelve además `NotRequired` (no hay PIN) y `Unavailable` (la verificación terminó tras salir de la pantalla/bloquear el dispositivo: puede contabilizar el intento pero no emite sesión). `MutationResult` añade `Unavailable` y `AuthenticationRequired` como en §7.
- **`OnboardingRepository.acceptOnboarding()`** (nuevo): marca `onboarding_accepted = true` por el mismo coordinador y `Mutex`. **No exige sesión** (no cambia reglas ni credenciales) y es idempotente; con el estado ilegible devuelve `RecoveryRequired`. Solo se confirma tras persistir.
- **`FocusStateRepository`** añade `retry()` para los `ReadError` reintentables. Su proyección (`LauncherSettings`) nunca incluye el verificador ni los contadores.
- **Restablecer** (`resetConfiguration`) escribe el estado inicial v1 con la clave actual, por lo que deja `onboarding_accepted = false` y la app vuelve a la Bienvenida.
- **Espera tras reinicio:** si el archivo indica espera activa y el proceso no la ha visto, se reaplica su duración completa desde el reloj monotónico actual (también tras reiniciar el dispositivo). Verificado en API 31 y 35.
- **Orden de duraciones** por nivel: `[30, 60, 120, 240, 480, 900]` s; el nivel se conserva al vencer la espera y se reinicia con un acierto.
- **Fallos del almacén** (`StateFailure.Kind`): `Corrupt`, `FutureVersion`, `TooLarge`, `InvalidState`, `KeyLost`, `InterruptedInit` ⇒ `RecoveryRequired`; `KeyUnavailable`, `Io` ⇒ `ReadError` reintentable. Solo se transporta la clase del fallo, nunca datos.
- **Borrar datos desde Android** elimina también la clave de Keystore (comprobado en API 31, 34 y 35), por lo que Focus arranca como instalación limpia y no queda atascado en `InterruptedInit`.
- **Casos de prueba obligatorios (§10):** están cubiertos por pruebas unitarias e instrumentadas (Keystore real); ver `docs/validation/T04.md` y `T06.md`.
