# Focus Launcher — Plan de implementación y aceptación

Versión: 1.0 · Fecha: 2026-11-03 · Estado: tareas propuestas; ninguna marcada como implementada o probada.

Referencias: [PRD.md](PRD.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DATA.md](DATA.md) y [CLAUDE.md](CLAUDE.md).

## 1. Forma de trabajo

Un desarrollador Android con experiencia puede planificar aproximadamente 4 semanas, sujeto a compatibilidad OEM, pruebas y disponibilidad de equipos. Es una estimación, no un compromiso. Priorizar un recorrido vertical y resolver temprano Home, catálogo y cifrado; no posponer integración Android hasta después de construir toda la UI.

Estados de cada tarea: pendiente → en curso → validada o bloqueada con causa y siguiente acción. Solo marcar validada con evidencia. Cada PR/cambio incluye requisitos cubiertos, pruebas ejecutadas y limitaciones pendientes.

Dependencias: T01 habilita T02, T03 y T04; T05 necesita esas tres. T06 necesita T02 y T04. T07 necesita T03–T06; T08 necesita T02, T04, T06 y T07; T09 necesita T05–T08. T10 integra T01–T09 y T11 depende de T10. Esta relación permite planificar trabajo sin exigir agentes paralelos.

## 2. Tareas implementables

### T01 — Base del proyecto y registro de decisiones

- [x] Crear `:app`, package `com.focuslauncher`, Kotlin, Compose/Material 3, Hilt, Coroutines, Lifecycle, DataStore tipado y Protobuf.
- [x] Fijar `minSdk = 31` y versiones estables compatibles de SDK/JDK/AGP/Gradle/Kotlin/Compose/Hilt; guardar catálogo de versiones y wrapper.
- [x] Añadir estructura de capas, dispatchers y reloj inyectables; configurar pruebas unitarias y Compose/instrumentación.
- [x] Copiar estos cinco documentos a raíz del repositorio; agregar instrucciones de build y registro de versiones/equipos al README.

Estado: **validada** (2026-11-03), evidencia en `docs/validation/T01.md`.

Dependencias: ninguna. Cubre RNF-01, RNF-03, RNF-06.

Aceptación: build debug y prueba unitaria de muestra se ejecutan desde checkout limpio; Hilt crea la Activity y ViewModel; no hay permisos fuera del alcance ni backend. Registrar comandos y versión del entorno, sin afirmar compatibilidad aún no probada. No crear una batería de tests vacíos.

### T02 — Entrada Home y ciclo de vida

- [x] Registrar Activity exportada con filtros HOME/DEFAULT y LAUNCHER separados; `singleTask` y manejo de `onNewIntent`.
- [x] Implementar HomeRoleController y solicitud oficial por acción explícita.
- [x] Manejar aceptación/cancelación, rol no disponible, pérdida del rol y reanudación.
- [x] Definir navegación Home/cajón/configuración/bienvenida, sin deep links de administración.

Estado: **implementada; validación parcial** (2026-11-03). Verificada en API 34; pendiente API 31 y 35+ (sin imágenes instaladas). Evidencia en `docs/validation/T02.md`.

Dependencias: T01. Cubre RF-02, parte de RF-09 y RF-14.

Aceptación: instalar en API 31 y API 35+, elegir Focus como Home, pulsar Home repetidamente sin duplicar Activities, volver desde una app y cambiar al launcher anterior. Cancelar la solicitud deja una vista utilizable y no vuelve a pedir rol sin acción. No se usa BOOT_COMPLETED para simular la selección Home.

### T03 — Adaptador Android de catálogo y lanzamiento

- [x] Obtener handle actual y serial válido; consultar LauncherApps solo para ese perfil.
- [x] Declarar `<queries>` MAIN/LAUNCHER e intersectar componentes reales mediante PackageManager según arquitectura.
- [x] Implementar AppId/AppPackageKey, deduplicación, etiquetas/orden e iconos con fallback y caché acotada.
- [x] Lanzar componente con LauncherApps; capturar app retirada, no disponible y fallo de permisos.
- [x] Registrar/cancelar callbacks y refrescar en reanudación, eventos de paquete y locale.

Estado: **validada en emulador API 34** (2026-11-03); perfiles reales, API 31/35 y OEM quedan para T10. Evidencia en `docs/validation/T03.md`.

Dependencias: T01. Cubre RF-03, RF-04, RF-12, RF-13, RNF-03.

Aceptación: comparar con fixtures de una app con dos actividades, una sin actividad launcher y Focus. Las dos actividades aparecen una vez cada una; ninguna entrada sintetizada de detalles entra al catálogo. Abrir ambas selecciona componente correcto. Un serial inválido genera error explícito. Instalar/desinstalar/actualizar durante el uso no causa cierre. No se solicita QUERY_ALL_PACKAGES ni se agregan apps de trabajo/privadas.

### T04 — Estado local tipado y cifrado

- [x] Implementar schema v1, mappers e invariantes de DATA.
- [x] Crear DataStore singleton en noBackupFilesDir con serializer AES-GCM y alias Keystore definidos.
- [x] Separar bootstrap nuevo de error/recuperación; modelar Loading/Ready/ReadError/RecoveryRequired.
- [x] Configurar exclusión de backup/transferencia y revisar manifiesto combinado.
- [x] Probar escritura transaccional y lectura tras recrear repositorio/proceso sin instancias simultáneas del mismo archivo.

Estado: **validada en emulador API 34** (2026-11-03); D2D, API 31/35+ y OEM quedan para T10. Evidencia en `docs/validation/T04.md`.

Dependencias: T01; integrar con T02/T03 al cerrar tarea. Cubre RF-11, RF-15, RF-16, RNF-03, RNF-05.

Aceptación: round-trip instrumentado con Keystore real; cada escritura cambia IV; archivo alterado, clave perdida, schema futuro y alias sin archivo no producen defaults ni borran datos. Instalación limpia crea estado válido explícito. Sin ReplaceFileCorruptionHandler que abra los ajustes. Política e intentos sobreviven un reinicio normal. Evidencia de que los datos no entran en los mecanismos de copia probados.

### T05 — Política visual y recorrido Home/cajón/búsqueda

- [ ] Implementar ObserveVisibleApps y LaunchApp con revalidación antes de abrir.
- [ ] Home: primeras 8 entradas visibles; cajón: todas; cuatro columnas y tema del sistema.
- [ ] Búsqueda en lista filtrada con comparación sin mayúsculas/acentos, sin historial persistente.
- [ ] Diseñar estados vacío, carga y fallo; acceso permanente a configuración/ayuda.

Dependencias: T02, T03, T04. Cubre RF-04, RF-05, RF-06, RF-07, RF-11, RF-15.

Aceptación: fixtures con paquete de dos actividades y nombres con acentos; ocultarlo elimina ambas de las tres superficies, aun con consulta activa. Cero apps visibles deja ajustes accesibles. Demorar/fallar lectura de política nunca muestra catálogo sin filtrar. Un toque pendiente sobre app ya oculta se rechaza. Medir RF-05 según T10.

### T06 — PIN, limitación de intentos y sesión administrativa

- [ ] Implementar KDF, comparación, creación, cambio y desactivación según DATA, sin guardar PIN original.
- [ ] Implementar reserva de intentos previa a KDF, espera progresiva y recuperación tras proceso nuevo.
- [ ] Centralizar mutaciones administrativas y verificación bajo coordinador/Mutex con sesión opaca.
- [ ] Caducar sesión en 120 s, salida, Home, segundo plano, pantalla apagada/bloqueada y muerte del proceso.
- [ ] Evitar restauración de secreto/autorización; proteger PIN y administración frente a capturas/Recientes.

Dependencias: T04, T02. Cubre RF-07, RF-08, RF-09, RF-10, RF-11, RF-15, RNF-03.

Aceptación: pruebas de límites y ceros iniciales, confirmación, salt nuevo, token revocado y carrera entre cambio de credencial/mutación. Simular KDF pausada, cancelar/matar tras reserva, acertar quinta y fallar escrituras. La espera escala hasta 900 s; reiniciar no la reduce; cambiar reloj no la acorta. Rotación no prolonga plazo y proceso nuevo no abre administración. Un booleano o ruta restaurada no permite saltarse PIN. Si la KDF termina después de salir de la pantalla, su resultado no emite sesión ni reabre ajustes. Instrumentar coste real de 600000 iteraciones.

### T07 — Editor y ajustes protegidos

- [ ] Agrupar catálogo por paquete; controles de visible/oculto y resumen de múltiples actividades.
- [ ] Guardar cada cambio por el coordinador; reflejar estado confirmado, pendiente o error.
- [ ] Incorporar pantallas de PIN y bloqueo según estado persistido real.
- [ ] Confirmar desactivación de PIN y restablecimiento; conservar tombstones al desaparecer paquetes.

Dependencias: T03, T04, T05, T06. Cubre RF-05, RF-07, RF-08, RF-12, RF-14, RF-15.

Aceptación: sin PIN se puede editar; habilitado, solo tras verificar y mientras siga vigente. Cambio fallido no aparenta éxito. Reinstalar un paquete oculto conserva ocultación; nueva actividad del mismo paquete también queda oculta. Con sesión caducada, el editor no retiene ni dibuja etiquetas ocultas y sus acciones se rechazan.

### T08 — Bienvenida, ayuda y recuperación

- [ ] Mostrar alcance visual, PIN opcional, apps nuevas visibles y soporte de perfil local.
- [ ] Guardar aceptación de aviso y ofrecer selección Home sin bloquear la prueba si se cancela.
- [ ] Ayuda pública y salida segura a Ajustes de Android, con alternativa si no hay Activity disponible.
- [ ] Añadir error temporal con reintento y RecoveryRequired sin reset interno no autenticado.
- [ ] Implementar restablecimiento interno confirmado, autorizado y transaccional.

Dependencias: T02, T04, T06, T07. Cubre RF-01, RF-02, RF-13, RF-14, RF-15.

Aceptación: PIN olvidado/clave perdida tiene instrucciones utilizables para cambiar Home y borrar datos desde Android. No hay PIN maestro ni pregunta secreta. Restablecer internamente con PIN requiere sesión vigente; tras commit deja bienvenida, política vacía y PIN deshabilitado. Se informa de la pérdida de reglas. Borrar datos desde Android funciona como vía externa, sin prometer que Focus lo impide.

### T09 — Accesibilidad y privacidad de presentación

- [ ] Probar TalkBack, foco, etiquetas/estados y objetivos táctiles.
- [ ] Corregir fuente 200 %, teclado, insets, orientación y navegación gestual/3 botones.
- [ ] Revisar Home, administración y miniaturas de Recientes; no exponer nombres ocultos tras bloquear sesión.
- [ ] Revisar logs y estado guardado para excluir PIN, verificadores y catálogos administrativos.

Dependencias: T05, T06, T07, T08. Cubre RF-06, RF-09, RNF-02, RNF-03.

Aceptación: todos los recorridos P0 operables con TalkBack y fuente 200 %; ninguna acción esencial queda cortada. Volver de Recientes o rotar durante PIN no restaura texto secreto/autorización caducada. Pantallas protegidas no permiten captura ni filtran miniatura en los dispositivos probados.

### T10 — Validación integrada, plataforma y rendimiento

- [ ] Ejecutar unitarias, instrumentadas, Compose y matriz manual de la sección 3.
- [ ] Medir objetivos RNF-04 en build release con fixture/entorno descritos; registrar p95, muestra y equipo.
- [ ] Validar modo avión, arranque frío, reinicio de teléfono después del primer desbloqueo y eventos de paquetes.
- [ ] Revisar manifiesto combinado, backup/D2D, permisos y ausencia de consultas multiperfil.
- [ ] Resolver fallos P0 y repetir solo las comprobaciones afectadas más el recorrido básico.

Dependencias: T01–T09. Cubre todos los RF y RNF.

Aceptación: matriz con resultado y evidencia por entorno; cero fallos de filtrado/autorización persistencia. Si no existe equipo Samsung o entorno de transferencia, marcar pendiente y no declarar ese soporte validado. No rebajar automáticamente requisitos ni ampliar permisos para conseguir resultados verdes.

### T11 — Entrega del MVP

- [ ] Preparar APK de prueba y notas con limitaciones, versiones y dispositivos probados.
- [ ] README reproducible: build, instalación, selección Home, uso, PIN olvidado y vuelta al launcher anterior.
- [ ] Sincronizar documentos si hubo cambios aprobados de contrato; completar trazabilidad y pendientes.
- [ ] Revisar que no se adjunten claves de firma, PIN de prueba persistido, datos personales ni logs sensibles.

Dependencias: T10 aprobado. Cubre RNF-01, RNF-06 y puerta de salida de PRD.

Aceptación: otra persona puede instalar, elegir Home, ocultar, proteger ajustes, reiniciar y recuperar siguiendo únicamente la documentación. Se entrega evidencia real; publicación en tienda no forma parte de esta tarea. No declarar completo si quedan fallos P0.

## 3. Matriz mínima de pruebas del sistema

| Entorno / situación | Prueba y resultado esperado |
| --- | --- |
| AOSP/Pixel API 31 (Android 12) | Rol Home, persistencia, PIN, cifrado, lanzamiento, navegación y reinicio tras desbloquear. |
| AOSP/Pixel API 34 (Android 14) | Flujo completo y cambios de paquetes; permisos/consultas acotadas. |
| AOSP/Pixel API 35 (Android 15) | Flujo completo; crear perfil laboral y Espacio privado de prueba con otro launcher/herramienta del sistema; Focus no consulta ni agrega sus apps. No promete gestionarlos. |
| Última versión Android estable al validar | Registrar API/build exactos; build instalable y recorrido completo con targetSdk elegido. No adivinar la versión. |
| Samsung One UI físico | Registrar modelo/versión; seleccionar/cambiar Home, navegación, Recientes, pantalla apagada, reinicio y regreso desde app. |
| Modo avión | Bienvenida, catálogo, búsqueda, cambios y PIN sin conexiones. |
| Proceso y lifecycle | Rotación, segundo plano, Home, pantalla bloqueada, cierre del proceso durante KDF y durante espera. |
| Integración de paquetes | Paquete con 2 actividades/alias, app sin LAUNCHER, inhabilitada, actualización, desinstalación/reinstalación y nueva instalación. |
| Error de almacenamiento | IO fallida, archivo alterado, alias perdido, schema futuro; sin catálogo abierto ni reset de PIN. |
| Backup/transferencia disponible | No restaurar archivo protegido/credenciales/reglas; registrar limitaciones del fabricante y mecanismo probado. |

Para pruebas que alteran perfiles, apps, reloj o datos usar exclusivamente dispositivos/emuladores de prueba y fixtures. No borrar datos del teléfono personal como prueba automática.

## 4. Trazabilidad compacta

| Requisitos | Tareas principales |
| --- | --- |
| RF-01 | T08 |
| RF-02 | T02, T08 |
| RF-03 | T03 |
| RF-04 | T03, T05 |
| RF-05, RF-06 | T05, T07, T09 |
| RF-07, RF-08 | T06, T07 |
| RF-09, RF-10 | T06, T09 |
| RF-11 | T04, T05, T06 |
| RF-12 | T03, T07 |
| RF-13 | T03, T08, T10 |
| RF-14 | T02, T07, T08 |
| RF-15 | T04, T05, T06, T07, T08 |
| RF-16 | T04, T10 |
| RNF-01 a RNF-06 | T01, T09, T10, T11 según detalle anterior |

## 5. Comprobaciones automatizadas previstas

Tras crear el proyecto, usar los comandos de [CLAUDE.md](CLAUDE.md). Unitarias verifican lógica con repositorios/relojes falsos; instrumentación valida Keystore/DataStore y UI. Las pruebas reales del rol y OEM requieren el sistema operativo, no una sustitución simulada.

Guardar evidencia en `docs/validation/` del futuro repositorio: entorno, comando/recorrido, resultado y defecto relacionado. Las capturas deben usar apps y credenciales de prueba. Este documento es un plan; no constituye un informe de pruebas ejecutadas.
