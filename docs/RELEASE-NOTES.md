# Focus Launcher — Notas de la versión 0.1.0 (MVP de prueba)

Fecha: 2026-11-03 · Estado: **MVP candidato, validado en emuladores.** No es una versión para tienda y **no debe presentarse como validada en dispositivos reales** (ver «Limitaciones y pendientes de validación»).

## Qué se entrega
| Elemento | Dónde |
| --- | --- |
| Código fuente completo | Este repositorio (módulo `:app`, paquete `com.focuslauncher`) |
| APK de prueba | Se genera con `./scripts/build-test-apk.sh` → `dist/focus-launcher-0.1.0-test.apk` + `.sha256` (no se versiona; ver README §2) |
| Documentación normativa y trazabilidad | `PRD.md`, `ARCHITECTURE.md`, `DATA.md`, `TASKS.md` (§6: estado final por requisito), `CLAUDE.md` |
| Evidencia de validación real | `docs/validation/T01.md` … `T10.md` |
| Instalación, uso, recuperación | `README.md` |

**Artefacto verificado:** `focus-launcher-0.1.0-test.apk`, 3 132 211 bytes, SHA-256 `efb6cfd6d0e0a8ecfac2d2b3da1cedd46906e2a840c7d9f705d43854def8d544` (firmado con la clave de depuración de la máquina de pruebas, certificado SHA-256 `809f545d…d4c14`). Generado desde un checkout limpio y desde el repositorio de trabajo con el mismo resultado byte a byte. En otra máquina el hash será distinto porque la clave de depuración es distinta.

La publicación en una tienda **no** forma parte de esta entrega.

## Qué hace
Pantalla de inicio Android (Android 12+, `minSdk 31`) con: aviso de alcance, solicitud oficial del rol Home, Inicio con las 8 primeras apps visibles, cajón con todas las visibles y búsqueda local, editor de visibilidad por paquete con guardado inmediato, PIN opcional de 6 a 12 dígitos con límite de intentos y esperas progresivas, sesión administrativa de 2 minutos, ayuda pública con salida a Ajustes de Android, recuperación documentada y restablecimiento confirmado. Todo **offline**: sin cuentas, backend, telemetría ni anuncios; sin permiso `INTERNET`.

## Lo que NO es
- **No es un bloqueo parental ni de seguridad fuerte.** Ocultar una app solo cambia lo que muestra Focus; la app puede abrirse desde notificaciones, enlaces, Ajustes, Recientes, asistentes u otro launcher. El propietario puede cambiar de Home, borrar datos o desinstalar Focus.
- El PIN protege solo la configuración de Focus. El PIN olvidado **no se puede recuperar** (sin PIN maestro ni pregunta secreta).
- Solo gestiona el **perfil personal** del propietario: no hay perfiles de trabajo, usuarios secundarios ni Espacio privado (sus apps no aparecen).
- Sin widgets, accesos directos, favoritos editables, gestos, paquetes de iconos, horarios, estadísticas, exportación ni sincronización.

## Seguridad y privacidad de la entrega
- Estado cifrado en reposo (AES-256-GCM con clave de Android Keystore, no exportable) en `noBackupFilesDir`; excluido de copias y transferencias; PIN con PBKDF2-HMAC-SHA256 (600000 iteraciones, salt de 16 bytes) y comparación en tiempo constante; nunca se guarda ni se registra el PIN.
- Manifiesto combinado del release **auditado automáticamente** (`verifyReleaseManifest`): 1 permiso (el interno de AndroidX), 0 receivers/services, 1 provider no exportado, 1 actividad exportada, `allowBackup=false`.
- Revisión previa a la entrega: no hay claves de firma, `local.properties`, APK, capturas de pruebas ni registros en el repositorio (las únicas imágenes versionadas son el arte del icono y sus recursos); ninguna ruta absoluta ni dato personal en documentos o código; los PIN que aparecen en las pruebas son constantes de prueba (nunca se persiste un PIN real); el código principal no escribe registros ni guarda estado restaurable (prueba automática). El APK de prueba se firma con la clave de **depuración** de cada máquina y no incluye ninguna clave. Los commits llevan como autor la identidad configurada en git del propietario del repositorio (no están firmados criptográficamente).

## Versiones y entorno
Toolchain fijado en `docs/VERSIONS.md` (Gradle 9.6.1, AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, Hilt 2.60.1, DataStore 1.3.0, Protobuf 4.36.2; `compileSdk 37`, `targetSdk 36`, `minSdk 31`). Tamaño del APK release: ≈ 3,0 MB.

## Qué se probó y dónde
| Entorno | Resultado |
| --- | --- |
| Pruebas unitarias | **183/183** |
| Pruebas instrumentadas, Pixel 6 · **API 35** (Android 15) | **166/166** |
| Pruebas instrumentadas, Pixel 8 Pro · **API 34** (Android 14) | **166/166** |
| Pruebas instrumentadas, Pixel 6 · **API 31** (Android 12) | **0 fallos**; 93 ejecutadas y 73 omitidas (las comprobaciones ATF de accesibilidad no funcionan antes de API 34; se omiten explícitamente) |
| Modo avión | Suite completa con red inaccesible en los tres |
| Manual en emulador | Rol Home real (API 31/34/35), reinicio del dispositivo, cierre del proceso durante la KDF y la espera, pantalla apagada, borrar datos, copias, eventos de paquetes, perfil de trabajo y Espacio privado (API 35), archivo dañado, fuente 200 %, horizontal, modo oscuro, navegación de 3 botones |
| Rendimiento (Pixel 6 / API 35, 200 entradas) | Arranque en frío hasta lista filtrada p95 **494 ms** (≤ 2000), búsqueda p95 **100 ms** (≤ 150), cambio de visibilidad p95 **29 ms** (≤ 500), KDF del PIN **342 ms** (con indicador, > 300 ms) |

Detalle, muestras y método: `docs/validation/T10.md`. Las cifras de rendimiento son de **emulador**, no del dispositivo físico de referencia.

## Limitaciones y pendientes de validación
Ningún fallo P0 abierto, pero estas comprobaciones **no se han podido hacer** y la versión no debe declararse validada en ellas:
1. **Dispositivo físico de referencia (Pixel 6/API 35):** repetir el rendimiento (RNF-04); las cifras de arranque y de la KDF en un teléfono real pueden diferir.
2. **TalkBack real:** solo se verificó con el motor ATF y el árbol de semántica (en el emulador no hay TalkBack); falta el recorrido P0 con lector de pantalla.
3. **Miniatura de Recientes:** se verificó que las pantallas administrativas y de PIN bloquean la captura (`FLAG_SECURE`), pero no se pudo ver la miniatura en un dispositivo.
4. **Samsung One UI y otros fabricantes:** selección de Home, navegación, Recientes, pantalla apagada, reinicio; las rutas de Ajustes varían por fabricante.
5. **Última versión estable de Android (en el SDK existen API 36, 36.1 y 37.0):** sin imagen instalada, no validada; `targetSdk 36` sin revisar contra ella.
6. **Transferencia dispositivo a dispositivo (D2D):** solo se probó la exclusión de copias con `bmgr`; la transferencia real requiere dos equipos.
7. **Medición de la KDF y del arranque en el equipo de referencia:** ver 1.

Otras limitaciones conocidas:
- Con fuente al 200 % las etiquetas de apps pueden ocupar hasta 3 líneas y abreviarse (la etiqueta completa está en la descripción de accesibilidad y en el cajón/Configuración).
- La cuadrícula es fija de 4 columnas (decisión de producto del MVP).
- En el primer arranque, mientras se crea la clave de Keystore, Inicio se dibuja un instante sin apps antes de mostrar la Bienvenida (no se filtra ninguna app).
- Si el sistema se queda sin pantalla de ajustes de inicio, la Ayuda muestra la ruta manual.
- La clave de Keystore no sobrevive a una restauración desde copia (por diseño); tras borrar datos o reinstalar, Focus empieza desde cero.

## Cómo reproducir
Ver `README.md` (§2 obtener el APK, §3 instalar, §4–§7 uso y recuperación, §8 pruebas y mediciones). El flujo completo —instalar, elegir Home, ocultar, proteger con PIN, reiniciar y recuperar— se comprobó siguiendo únicamente ese documento sobre un APK generado desde un checkout limpio (ver `docs/validation/T11.md`).
