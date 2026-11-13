# Fase 2 — Navegación: diagnóstico y optimizaciones acotadas

13 de noviembre de 2026. Punto de partida: fase 1 sin commit, identificada por los hashes de [base_fase1.json](base_fase1.json). No se usa HEAD como si fuera fase 1: HEAD sigue siendo la versión 1.1 auditada. **No se han modificado parser, reproductores, buffers, reconexiones, persistencia, dependencias ni diseño visual durante esta fase.** No se ha introducido base de datos, hecho commits o publicado una release.

## Resultado y límite principal

Se han eliminado dos costes confirmados por código y reproducidos en JVM:

1. La reconstrucción de un mapa inverso con una entrada por resultado, incluso al volver a «Todos» sin búsqueda, ejecutada en Main.
2. La búsqueda lineal del canal seleccionado en la cabecera lateral cada vez que ese panel se recompone.

**No se ha demostrado que sean la causa exacta de la pausa de la primera pulsación en la Smart TV del usuario ni que esa pausa haya desaparecido.** No hay dispositivo Android conectado. Se entrega una comparación reproducible de esas operaciones, instrumentación para separar las etapas reales y un [procedimiento de prueba en TV](PRUEBA_EN_TV.md).

Compilación debug, `check` y Lint correctos: **52/52 tests, cero errores de Lint y los 56 avisos documentados en fase 1**. Se conservan los 47 tests anteriores (un fixture de reconciliación adaptado a la nueva representación) y se añaden cinco. Las sondas siguen desactivadas por defecto y solo pueden activarse explícitamente en debuggable.

## Observaciones reales que orientan el análisis

- TV Android de unos siete años, aplicación estable y menú del propio Android también lento; Kodi responde mejor.
- «Todos» ya está precargado y aparece casi inmediatamente.
- Pausa breve **al comenzar** a desplazarse; después las flechas responden normalmente. Grupos pequeños sin el mismo síntoma apreciable.
- Referencias de unas 150.000 y 2.500.000 **líneas M3U**, no recuentos de canales.

Estas observaciones sustituyen la interpretación anterior de retraso continuo por pulsación. El diagnóstico no incluye volver a medir el parser como explicación automática: aquí no se modifica ni se mide la lectura M3U. El coste de completar una carga y la latencia de la primera tecla son etapas distintas.

## Recorrido de entrada y primera pulsación

### Al cambiar grupo

`MainViewModel.selectGroup` actualiza grupo, guarda la preferencia en IO y llama a `scheduleFiltering(immediate=true, preferSync=true)`. Sin query y fuera de Favoritos, se usa la ruta síncrona. Los canales del grupo y «Todos» ya están indexados.

En fase 1, `computeFilterResult` **construía siempre** `HashMap<Int,Int>(K)` para los K resultados antes de publicar. Por tanto, reutilizar el catálogo no hacía constante el trabajo de volver a «Todos». Ese mapa se retenía para resolver selección/visibilidad, con boxing de claves/valores y posibles redimensionamientos. El usuario puede ver la lista rápido y aun así encontrarse con presión de asignaciones o trabajo cercano a la primera interacción; atribuir la pausa a esa coincidencia sigue siendo una **hipótesis**, no una medida Android.

Al presentar la lista se ejecuta el efecto de scroll si cambia grupo/tamaño/selección visible. Consulta solo los elementos visibles y llama a `scrollToItem` si es necesario. Las filas visibles tienen efectos y estados de interacción; las no visibles no se componen todas de antemano.

### Al pulsar DPAD arriba/abajo

1. Android entrega el evento al `Window.Callback` preparado en fase 1.
2. La sonda optativa mide despacho y CPU de Main; `handleKeyEvent` **no procesa estas flechas** como zapping. Las deja al callback original/Compose.
3. La búsqueda de foco de Compose y, cuando corresponde, la lista lazy realizan foco/desplazamiento.
4. La fila recibe foco y su `MutableInteractionSource` actualiza estado local. Cambian borde/texto/fondo según el código existente.
5. Composición, layout/draw y presentación son etapas posteriores o solapadas; el callback de foco no demuestra que el borde ya se haya presentado en pantalla.

No se encontró una llamada desde el cambio local de foco a `selectChannel`, `selectGroup`, `scheduleFiltering`, agrupación global o preparación del motor. La reproducción sigue iniciándose mediante activación/OK, no al enfocar. La sonda de filtro permite detectar si una traza real contradice esa lectura estática.

### Recomposición y framework

- La cabecera `SidePanel` tenía `filteredChannels.firstOrNull { originalIndex == selectedIndex }`: coste O(K) cuando el seleccionado está al final o fuera del grupo. Si está al principio es barato. **No se ha demostrado que SidePanel se recomponga por cada flecha**: depende de los estados/callbacks que cambien. Los ticks EPG y otros cambios amplios de estado son posibles disparadores independientes.
- Inspección con `javap` del bytecode real de fase 1: `ChannelList` y `ChannelRow` usan `Composer.changedInstance` para parte de sus entradas y no tienen iteradores globales en sus cuerpos directos. [Conteos](compose_fase1.json). No se deduce de esto el número real de recomposiciones ni el coste de Compose interno.
- La biblioteca resuelta conserva claves por índice original y `NearestRangeKeyIndexMap`; su constructor indexa el rango recibido, no presupone necesariamente el catálogo entero. [Bytecode inspeccionado](nearest_range_bytecode.txt). Esto no prueba el tamaño de todas las búsquedas internas de foco, por lo que no se acusa a `LazyColumn` ni se sustituye.
- La primera búsqueda de foco, compilación JIT/carga de clases, efectos de fila/marquee, dos mecanismos focusable de una fila (`combinedClickable` y `focusable` explícito), trabajo del sistema y GC siguen siendo **hipótesis pendientes**. No se eliminan comportamientos de foco o animación sin trazas que lo justifiquen.

## Cambios realizados y fundamento

### 1. Lookup ordenado sin mapa inverso

Nuevo helper en `Models.kt`: `findVisibleChannelIndex(entries, originalIndex)`.

- `mapIndexed` asigna índices originales únicos y crecientes. El agrupado agrega en ese orden y los filtros lo conservan. No hay ordenación de canales por nombre en estas rutas.
- Primero prueba directamente `entries[originalIndex]`: en «Todos» sin filtros devuelve el índice con **una lectura**.
- En subconjuntos usa búsqueda binaria, O(log K), sin crear un mapa. Devuelve −1 si el canal no pertenece al filtro.
- `visibleIndexForSelection` mantiene la política anterior: foco en la primera fila si el canal reproducido está fuera del grupo, sin cambiar ese canal; −1 si no hay resultados.

`MainViewModel` deja de crear/retener `filteredIndexByOriginalIndex`; cálculo de filtros, carga inicial y selección usan el lookup sobre los mismos resultados ordenados. Favoritos y búsqueda conservan sus recorridos necesarios para encontrar resultados, pero ya no construyen además el mapa inverso. El cambio no introduce una caché invalidable adicional ni duplicados de los canales.

Riesgo: la búsqueda binaria **requiere índices crecientes**. Si una fase posterior ordena resultados por otra columna deberá cambiar este contrato o añadir un índice adecuado. Los tests comprueban agrupado/filtrado actuales y comparan contra búsqueda lineal. El coste de localizar una selección en un grupo pasa de lookup O(1) con mapa precalculado a O(log K), a cambio de eliminar preparación y memoria O(K); se midió también el caso de grupo.

No se promete que toda la publicación de `UiState` sea O(1): si en otra ruta se comparan listas distintas del mismo tamaño y contenido puede intervenir igualdad estructural. Tampoco se han optimizado conteos de favoritos o el predicado de búsqueda. La propiedad de esta entrega es que **estas resoluciones de posición no recorren globalmente el catálogo ni reconstruyen su mapa**.

### 2. Cabecera sin búsqueda lineal

`AppScreens.kt`, `SidePanel`: sustituye `firstOrNull` por `getOrNull(findVisibleChannelIndex(...))`. Mismos resultados para canal presente, ausente o seleccionado −1; la cabecera no toma erróneamente la fila cero cuando el canal no está en el filtro. No se añaden `remember` con claves que comparen listas enormes ni cachés de contenido.

Riesgo bajo condicionado al mismo orden. No se cambia el aspecto, las claves de LazyColumn, scroll, requesters, interacción, favoritos o acciones de la fila.

### 3. Sondas mejoradas, sin activación normal

`NavigationDiagnostics.kt` y dos llamadas pequeñas en `AppScreens.kt`/`MainViewModel.kt`:

- Segmentos anónimos al presentarse un catálogo: contador, `all=true/false` y número de resultados. Referencia comparada con `===`, sin recorrer ni registrar contenido de listas.
- Número de evento y ordinal dentro del segmento para distinguir primer DOWN de siguientes. No se registra texto ni número/ID de canal.
- Secciones `miptvga.filter.sync` y `miptvga.filter.async` con duración y `main=true/false`.
- Despacho, CPU Main, edad del evento y cambio de foco con IDs correlacionables.
- Proxy de callback de frame posterior al foco, usando la misma base `System.nanoTime` que los timestamps de [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics). **No es una medida de píxeles presentados.** La cola del handler y la toma de muestras pueden retrasarlo; solo hay una muestra pendiente y puede ser sustituida.
- Se conservan frames, contadores de composición, GC, heap/PSS y StrictMode de fase 1. El detalle de activación y límites está en la guía de TV.

Solo funciona con `diagnostic_navigation=true` y `FLAG_DEBUGGABLE`. Desactivada, no crea listener/hilo de métricas ni logs, no conserva la lista activa y las llamadas retornan antes de recorrer datos. Activada añade coste de observación; no confundir esa ejecución con rendimiento final sin instrumentación.

## Comparación reproducible JVM

[Script](medir_navegacion.py), [CSV completo](mediciones_jvm.csv) y [comprobaciones de equivalencia](equivalencia_jvm.txt).

El script compila el método exacto de filtro de fase 1 guardado en [FiltroFase1.kt](FiltroFase1.kt), cambiando solo visibilidad/contenedor para aislarlo, y extrae el método actual del ViewModel. Ambos usan los modelos actuales: los cambios del modelo respecto a fase 1 añaden lookup, sin alterar los datos de las fixtures. El lookup anterior de cabecera se reproduce con el mismo `firstOrNull`; el nuevo con el helper real.

Entorno: Linux x86_64, Intel Core i7-10700, 16 hilos, RAM física ~15,5 GiB, OpenJDK 21.0.12.1, Kotlin embeddable 2.2.10. Un proceso por tamaño con `-Xms128m -Xmx1024m`; sin Gradle simultáneo en la ejecución medida. No hay aislamiento del resto del escritorio ni control térmico. No se leen listas privadas, no se ejecuta Android, no se mide parser ni se reproduce vídeo.

Fixtures: 100/1k/10k/100k/1M entradas, 100 grupos intercalados, metadatos/Channel compartidos de prueba. La creación de datos, agrupación y conteos queda fuera de la medición. Por eso sus asignaciones no representan el catálogo real completo. La muestra de grupo tiene N/100 canales, incluidos grupos de un solo canal en N=100.

Por operación: primera llamada **medida**, tres calentamientos y nueve llamadas medidas; mediana y máximo en CSV. No se interpreta esa primera llamada como primer DPAD o arranque en frío. En N=100/1k el lote previo de equivalencia ya calienta métodos; en otros tamaños no. El orden y JIT favorecen diferencias a escala de microsegundos; no dar precisión de TV a esos valores.

Las asignaciones provienen de ThreadMXBean del hilo actual, no de heap retenido/GC/PSS. Incluyen un overhead pequeño del harness (por ejemplo boxing al almacenar muestras); los ~24–72 B finales no son una medición de cero asignaciones absolutas. El resultado se consume mediante referencia volatile para evitar descartar el trabajo.

### Optimización del filtro al entrar en «Todos»

| Canales | Primera llamada fase1/fase2 ms | Mediana fase1/fase2 ms | Bytes asignados mediana fase1/fase2 |
|---:|---:|---:|---:|
| 100 | 0,060 / 0,018 | 0,00504 / 0,00047 | 4.904 / 56 |
| 1.000 | 0,100 / 0,021 | 0,05062 / 0,00044 | 72.392 / 72 |
| 10.000 | 4,386 / 0,444 | 0,67894 / 0,00143 | 701.624 / 72 |
| 100.000 | 18,866 / 0,691 | 3,90255 / 0,00143 | 7.968.968 / 72 |
| 1.000.000 | 82,907 / 0,668 | 46,98172 / 0,00236 | 76.578.984 / 72 |

La eliminación de la asignación proporcional a N es evidencia más sólida que el cociente de nanosegundos del caso constante. En 1M se eliminan unos **73 MiB de asignación temporal por llamada medida**; no equivale a reducción igual de PSS ni demuestra cuántas pausas GC desaparecen en ART.

### Optimización independiente de cabecera: seleccionado al final

| Canales en Todos | Mediana lineal fase1 ms | Mediana lookup fase2 ms |
|---:|---:|---:|
| 100 | 0,00521 | 0,00016 |
| 1.000 | 0,08467 | 0,00029 |
| 10.000 | 0,10309 | 0,00039 |
| 100.000 | 0,24453 | 0,00034 |
| 1.000.000 | 1,90399 | 0,00035 |

El CSV incluye también seleccionado primero y ausente. **No hay beneficio sistemático cuando ya estaba el primero**: ambos son constantes, y algunas primeras llamadas del helper nuevo son más lentas por calentamiento. Por tanto, este cambio no explica por sí solo una pausa inicial cuando el primer canal es el seleccionado.

En el grupo de 10.000 entradas de un catálogo de 1M, el filtro pasa de mediana 0,23164 ms a 0,00403 ms, eliminando también su mapa (~703.608 B → 72 B medidos). No se confunde este grupo sintético con los grupos concretos del usuario.

## Validación funcional y compilación

- Conservados los 47 tests anteriores, adaptando únicamente el fixture de `LatestTaskTest.filterUsesSelectionAtPublicationTime` a la lista ordenada en lugar de mapa. Se mantiene la misma aserción de selección vigente y fallback.
- Cinco nuevos casos en `ChannelLookupTest`: comparación con búsqueda lineal sobre grupos/filtros; ausencia sin seleccionar otro canal; invariantes de orden; una lectura para «Todos» de 1M; como máximo 21 lecturas para un subconjunto virtual de 1M. Las listas virtuales cuentan accesos sin reservar 1M objetos en los unit tests.
- Harness: **384 combinaciones en 100 canales y 384 en 1.000** comparan ambos filtros completos, incluyendo Todos, Favoritos, grupo normal/inexistente, query vacía/con coincidencia/sin coincidencia, favoritos de canal/grupo, selección −1/presente/ausente y preservación activada/desactivada. Canales, grupos, selección y selección visible coinciden.
- `./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:check --continue`: **BUILD SUCCESSFUL**, 52 tests, 0 errores y 56 avisos Lint. [Log](gradle-validacion.txt), [resumen](comprobaciones.txt), [Lint XML](lint-debug.xml).
- Generador y resumen de logs probados con datos sintéticos locales; los resultados de esa prueba del script no se presentan como medición Android. Captura por ADB preparada, no ejecutada sin TV.

## Archivos de esta fase y conservación del alcance

Código principal modificado respecto a los hashes de fase 1:

- `app/src/main/java/com/toigo/miptvga/Models.kt`: lookup ordenado y reconciliación.
- `app/src/main/java/com/toigo/miptvga/MainViewModel.kt`: elimina mapa inverso y mide bloques de filtrado bajo diagnóstico optativo.
- `app/src/main/java/com/toigo/miptvga/AppScreens.kt`: cabecera por lookup, metadatos anónimos de segmento.
- `app/src/main/java/com/toigo/miptvga/NavigationDiagnostics.kt`: correlación primer/siguientes DOWN, foco y proxy frame.
- `app/src/test/java/com/toigo/miptvga/LatestTaskTest.kt`: fixture adaptado conservando el test.
- `app/src/test/java/com/toigo/miptvga/ChannelLookupTest.kt`: cinco casos nuevos.
- `docs/fase2/`: método base, scripts, mediciones, procedimiento y evidencias.

Se han contrastado hashes para verificar que las demás fuentes principales y `app/build.gradle.kts` no cambian respecto a fase 1. El estado Git contiene además los cambios anteriores sin commit; no atribuirlos todos a esta fase.

## Pendientes y decisión de cierre

La raíz de la pausa visual del primer DPAD en la TV **sigue abierta**. Las optimizaciones se justifican por los costes eliminados, pero no se certifican fluidez, jank, tiempos de foco, CPU o GC en Android. Tampoco se ha certificado con unit tests que el motor no se reinicie en un dispositivo: esta fase no modifica sus rutas y el foco no solicita reproducción según el código revisado.

La guía permite que el usuario devuelva un TXT limitado al tag numérico y su resumen, separando primera/siguientes, Todos/grupos y espera/inmediata. Si el primer despacho es rápido y el foco/frame tarda, investigar búsqueda de foco/composición/render; si el despacho consume CPU Main, correlacionarlo con su traza; si ocurre junto a GC/asignaciones, medir ART antes de otra corrección. La igualdad de listas del estado amplio, efectos de foco y margen de memoria quedan como riesgos, no diagnósticos demostrados.

**Se detiene aquí la fase 2. No se inicia carga progresiva, base de datos, refactorización multimedia, nueva UI ni Salir.** Antes de otra fase se requiere autorización; los datos de TV pueden justificar una continuación específica del diagnóstico sin ampliar a esos trabajos.
