# Focus Launcher — PRD del MVP

Versión: 1.0 · Fecha: 2026-11-03 · Estado: especificación para implementar, sin código de aplicación entregado.

## 1. Propósito y límite del producto

Focus Launcher es una pantalla de inicio Android para organización personal o familiar. Permite decidir qué aplicaciones aparecen en sus propias superficies y proteger su configuración con un PIN opcional. Funciona íntegramente offline, sin cuentas, backend, telemetría ni anuncios.

**Ocultar una aplicación solo cambia su presentación en Focus Launcher.** Puede seguir ejecutándose desde notificaciones, enlaces, Ajustes, Recientes, asistentes u otro launcher. El PIN protege la configuración de Focus; no modifica la autenticación de Android. El propietario del dispositivo puede cambiar Home, borrar datos o desinstalar Focus. Este producto no ofrece un bloqueo parental inviolable.

## 2. Decisiones del MVP

| Tema | Decisión normativa |
| --- | --- |
| Plataforma | Android 12+; `minSdk = 31`. `compileSdk` y `targetSdk` estables y compatibles, fijados en T01; no se confunden con minSdk. |
| Stack | Kotlin, Compose, Material 3, MVVM/Clean ligera, Hilt, Coroutines y StateFlow. |
| Integración | LauncherApps para catálogo/ejecución, RoleManager `ROLE_HOME` para selección oficial de Home. |
| Datos | Un DataStore tipado con Protobuf; archivo cifrado con una clave de Android Keystore. Ver [DATA.md](DATA.md). |
| Unidad de presentación | Cada actividad launcher real es una entrada; identidad por componente y serial de usuario. |
| Unidad de ocultamiento | Paquete y serial de usuario: ocultar un paquete elimina todas sus entradas. |
| Política inicial | Lista de paquetes ocultos vacía; apps nuevas visibles por defecto. No hay lista de permitidos en este MVP. |
| PIN | Opcional, deshabilitado de inicio; de 6 a 12 dígitos ASCII. No hay PIN predeterminado. |
| Perfiles Android | Solo el perfil de ejecución; no se agregan perfiles de trabajo, privados ni otros usuarios. |
| Personalización | Cuadrícula fija de 4 columnas en teléfono y tema del sistema. Sin editor de diseño. |

Estas decisiones concretan detalles no fijados en la conversación previa. La política de apps nuevas y el PIN opcional deben explicarse durante la bienvenida.

## 3. Alcance y usuarios

Historias principales:

- Como usuario, elijo Focus como Home y abro mis aplicaciones visibles.
- Como usuario, busco una aplicación sin que las ocultas aparezcan en resultados o sugerencias.
- Como adulto que configura un teléfono, oculto o muestro paquetes y protejo los ajustes con PIN.
- Como propietario, conozco las limitaciones y puedo volver al launcher del sistema.

P0: bienvenida, solicitud de Home, Home, cajón, búsqueda local, catálogo actualizado, editor de visibilidad, PIN opcional, persistencia y recuperación documentada.

Fuera del MVP: widgets, accesos directos de apps, favoritos editables, gestos configurables, paquetes de iconos, horarios, estadísticas, cuentas, importación/exportación y sincronización. También quedan fuera los modos de producto «Personal/Parental» y los perfiles de reglas. Esos modos no deben confundirse con los perfiles Android.

No se usarán root, Device Owner/Profile Owner, Device Admin, Lock Task, Accessibility Service, Usage Access, superposiciones ni acceso a notificaciones. No se impedirá cambiar Home, instalar/desinstalar o acceder a Ajustes.

## 4. Recorrido y pantallas

1. Bienvenida: función, límites, perfil soportado y apps nuevas visibles; aceptación local del aviso.
2. Solicitud explícita de Home mediante diálogo Android. Cancelarla permite probar Focus; no se repite automáticamente.
3. Home: cuadrícula de las primeras 8 entradas visibles, ordenadas por etiqueta; botón «Todas las aplicaciones», búsqueda y acceso permanente a «Configuración de Focus».
4. Cajón: todas las entradas visibles; búsqueda sobre el mismo conjunto. Sin recomendaciones de apps ocultas.
5. Configuración: si el PIN está habilitado, primero autenticación. Editor agrupado por paquete con estado visible/oculto; cada cambio se guarda inmediatamente, sin botón adicional de guardar. Indicador de guardado/error.
6. Seguridad: crear PIN y confirmarlo; cambiarlo o deshabilitarlo requieren una sesión administrativa vigente. Sin PIN, los ajustes son accesibles directamente.
7. Ayuda: límites, apps nuevas, perfil local y recuperación externa. La ayuda y la salida a Ajustes de Android siempre son accesibles, incluso sin PIN.

Si se ocultan todas las aplicaciones, Home muestra un estado vacío con acceso a Configuración de Focus y ayuda. El propio paquete Focus se excluye del catálogo y nunca se presenta como elemento ocultable. No se bloquean las barras, botones o gestos del sistema.

## 5. Requisitos verificables

Los identificadores son estables y se trazan en [TASKS.md](TASKS.md).

| ID | Requisito y criterio de aceptación |
| --- | --- |
| RF-01 | En una instalación nueva, se muestra el aviso de alcance antes de configurar visibilidad. Aceptarlo persiste; el aviso sigue disponible en ayuda. |
| RF-02 | Focus figura como candidato Home. Solicitar `ROLE_HOME` muestra el flujo oficial cuando está disponible; aceptar, cancelar o perder el rol no causa un cierre ni bucle. El estado se vuelve a comprobar al reanudar. |
| RF-03 | El catálogo contiene entradas launcher reales, habilitadas y accesibles del perfil de ejecución, sin Focus ni duplicados por identidad. No se promete inventario completo de paquetes instalados. |
| RF-04 | Tocar una entrada visible abre su componente en el perfil correcto. Si desaparece o deja de estar disponible, hay mensaje y refresco, sin cierre. |
| RF-05 | Ocultar un paquete elimina todas sus actividades de Home, cajón y búsqueda; mostrarlo las restaura si siguen disponibles. Cambio efectivo tras confirmación del guardado, en menos de 500 ms en la referencia de RNF-04. |
| RF-06 | La búsqueda recorta espacios y compara etiquetas sin distinguir mayúsculas ni acentos. Solo recibe entradas ya filtradas; al ocultar una app con consulta activa desaparece también de esos resultados. No hay sugerencias ni historial persistente. |
| RF-07 | Configuración siempre es alcanzable con catálogo vacío o fallo de carga. Si PIN está deshabilitado, los cambios están permitidos; si está habilitado, ninguna mutación administrativa se ejecuta sin autorización vigente, incluso por navegación restaurada. |
| RF-08 | Crear PIN exige 6–12 dígitos y confirmación coincidente. No se guarda texto original ni hash rápido. Verificación, comparación, cifrado y limitación siguen [DATA.md](DATA.md). PIN incorrecto nunca abre ajustes. |
| RF-09 | La autorización dura como máximo 2 minutos desde el desbloqueo y caduca al salir de administración, recibir Home, ir a segundo plano, apagar/bloquear pantalla o morir el proceso. Rotar no extiende el plazo. Volver exige autenticación cuando corresponde. |
| RF-10 | Tras 5 reservas de intento se inicia una espera de 30 s para intentos posteriores; si no hay éxito, los siguientes grupos aplican 60, 120, 240, 480 y máximo 900 s. Cancelar o matar el proceso no devuelve intentos reservados. Reiniciar un proceso con espera activa reaplica su duración completa. |
| RF-11 | Visibilidad, aviso, configuración del PIN y estado de intentos sobreviven a cierre y reinicio normal. La sesión y el texto del PIN no se restauran. No hay pantalla con apps sin filtrar mientras cargan las preferencias. |
| RF-12 | Instalación, actualización, cambio de habilitación y desinstalación refrescan el catálogo. Apps nuevas aparecen por defecto; una app antes oculta sigue oculta al reinstalarse con el mismo paquete en ese perfil. No se borra su regla al desaparecer temporalmente. |
| RF-13 | No se consultan ni muestran apps de perfiles asociados. En Android 15+ no se solicita `ACCESS_HIDDEN_PROFILES`; Espacio privado no tiene soporte funcional. La limitación está en bienvenida y ayuda, y no se presenta como espacio vacío controlado. |
| RF-14 | Ayuda explica que el PIN olvidado no se recupera. Hay salida a Ajustes de Android; borrar datos/desinstalar desde Android reinicia la configuración y elimina las reglas. Restablecer dentro de Focus exige sesión vigente si hay PIN y confirmación explícita de pérdida de datos. |
| RF-15 | Lectura/cifrado/versión incompatibles muestran error recuperable o `RecoveryRequired`, sin convertir PIN a deshabilitado ni mostrar catálogo sin filtrar. Las escrituras fallidas no se confirman visualmente como éxito. |
| RF-16 | No se exportan los datos del MVP a nube, transferencia ni almacenamiento compartido. No hay permiso `INTERNET` en el manifiesto final. |

## 6. Calidad y seguridad

| ID | Criterio de aceptación |
| --- | --- |
| RNF-01 | APK instalable en API 31+; pruebas funcionales en Android 12, 14, 15 y versión estable disponible al validar. Matriz y versiones exactas registradas. Compatibilidad futura sujeta a pruebas, sin promesa para cualquier OEM. |
| RNF-02 | Etiquetas completas para TalkBack, orden de foco coherente, controles táctiles de al menos 48 dp, estado visible/oculto anunciado y sin texto cortado que impida operar a escala de fuente 200 %. |
| RNF-03 | Consultas, iconos, DataStore y derivación del PIN fuera del hilo principal; sin ANR ni trabajo criptográfico en Composables. Ningún registro, estado guardado o captura de prueba contiene el PIN. |
| RNF-04 | Objetivos medidos en build release, Pixel 6/API 35 o dispositivo de referencia equivalente registrado, con 200 entradas: arranque frío hasta lista filtrada ≤ 2 s p95 en 20 ejecuciones; búsqueda ≤ 150 ms p95 en 30 consultas; cambio confirmado a UI ≤ 500 ms p95 en 20 cambios. No son mediciones ya obtenidas. El PIN debe tener indicador si tarda > 300 ms; se mide separado. |
| RNF-05 | En modo avión se completan todos los recorridos P0. El manifiesto combinado no contiene permisos/servicios fuera del alcance. No hay analítica ni informes remotos de fallos. |
| RNF-06 | Pruebas unitarias de política, autorización, intentos y errores; instrumentadas de DataStore/Keystore y Compose; validación manual de Home y OEM. No se declara una plataforma validada sin evidencia. |

## 7. Precisión sobre Android y perfiles

«Visible para Android» significa que una API permite descubrir información de otro paquete. «Visible en Focus» es una regla visual propia. Modificar esta última no cambia permisos, estado instalado ni ejecución del paquete. Las consultas de PackageManager pueden estar filtradas; una declaración `<queries>` acotada no es un permiso de administración. Véase [visibilidad de paquetes](https://developer.android.com/training/package-visibility) y [declaración de consultas](https://developer.android.com/training/package-visibility/declaring).

Perfil soportado significa `Process.myUserHandle()`, no necesariamente el usuario con número 0. El MVP se valida en el perfil personal del propietario; ejecución dentro de un perfil administrado no está soportada. No hay vista combinada de trabajo, usuarios secundarios, clones OEM o Espacio privado. No se infiere que todo perfil distinto al principal sea laboral. [Android 15 y Espacio privado](https://developer.android.com/about/versions/15/behavior-changes-all#private-space).

## 8. Entrega y éxito

MVP aceptado cuando todos los RF y RNF P0 están cubiertos por T01–T11, sin defectos que filtren apps ocultas, permitan mutaciones sin PIN o impidan volver a Home. La entrega será APK de prueba, código y evidencia de validación; la publicación en una tienda es una etapa posterior.

Este paquete de documentos es la entrada para esa implementación. [ARCHITECTURE.md](ARCHITECTURE.md) define componentes, [DATA.md](DATA.md) contratos y persistencia, [TASKS.md](TASKS.md) ejecución y [CLAUDE.md](CLAUDE.md) reglas operativas.
