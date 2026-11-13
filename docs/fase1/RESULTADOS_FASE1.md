# Fase 1 — Saneamiento y fiabilidad

13 de noviembre de 2026. Base auditada: `8a2e87f` (1.1). Variante A aprobada; sin rediseño visual en esta entrega. **La fase 2 no ha comenzado**: únicamente se prepararon sondas optativas y su procedimiento de medición. No se han cambiado buffers, motores, minSdk, ABIs, identidad, firma, versión, claves persistidas ni credenciales. No hay commits ni publicación de APK/releases.

## Resultado verificable

| Comprobación | Línea base auditada | Fase 1 |
|---|---:|---:|
| Ensamblado debug | Correcto | **Correcto** |
| Tests JVM | 27/30 | **47/47**, cero fallos/errores |
| Android Lint debug | 147 errores, 56 avisos | **0 errores, 56 avisos** |
| Gradle `check` | Fallido | **Correcto** |
| Compilación test Android XMLTV | No preparado | **Correcta** |
| Ejecución en Android/API 24/TV | No realizada | **Pendiente**, sin dispositivo |

Comandos finales ejecutados con caché y permisos de escritura Gradle disponibles:

```bash
./gradlew --no-daemon --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:check --continue
./gradlew --offline --no-daemon :app:compileDebugAndroidTestKotlin :app:lintDebug :app:check
```

Primero: `BUILD SUCCESSFUL in 48s`. Segundo: `BUILD SUCCESSFUL in 12s`. Son duraciones del build completo con caché, no de rendimiento de la app. Hubo iteraciones fallidas antes del resultado final: un fallo interno transitorio de Lint al analizar la versión intermedia de MainActivity, un opt-in restante en UI duplicada y una firma de test HTTP incompatible con JUnit (retorno inferido no Unit). Se corrigieron, sin deshabilitar detectores, sin baseline y sin ignorar tests. No se ocultan esos resultados como si todos los intentos hubieran pasado.

Salidas originales: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`. Copias portables resumidas y XML en esta carpeta. El APK generado es únicamente un artefacto debug local, no se ha publicado.

## Problemas corregidos

### Tests y CI

- Eliminadas de `.gitignore` las exclusiones globales de tests. Los cuatro archivos preexistentes se conservan íntegros salvo ampliaciones; ahora son visibles y versionables junto con los nuevos. Permanecen en el árbol de trabajo sin commit ni staging automático.
- CI ya exige tests sin `continue-on-error` y añade `lintDebug` obligatorio antes de subir su artefacto debug habitual. No se ha ejecutado el workflow remoto; se ejecutaron sus comprobaciones equivalentes localmente.
- No se actualizaron bibliotecas de producción. Única dependencia adicional: `kxml2:2.3.0`, **solo testImplementation**, para ejecutar el parser XMLTV original en JVM en lugar del stub Android de XmlPullParser. No aumenta el APK.

### Contrato de URLs Xtream ambiguas

Los tres fallos originales se debían a mezclar dos preguntas: «¿existe evidencia de que es directo?» y «¿conviene recuperar la conexión?». Una URL genérica `get.php` sin indicios live devuelve false en `isLikelyLiveStream`; se mantienen `shouldReconnectAfterEnd` y la exclusión de caché según compatibilidad Xtream. `type=live`, `type=m3u_plus`, `output=ts/m3u8` y las rutas live conocidas siguen clasificándose como directo. VOD conocido continúa siendo VOD.

Se preservan las expectativas de los tests existentes y se añade una prueba explícita de ese contrato. El cambio está localizado en la heurística; no cambia buffers, manejo del player ni consulta proveedores reales. Esta clasificación por URL es una estimación: la política de sesión basada en capacidades reales se revisará en fase 4.

### M3U

Se conserva el lector/algoritmo principal. Únicos cambios: callback opcional de cancelación (comprobado cada 256 líneas y al terminar) y localización del primer separador EXTINF fuera de atributos entrecomillados. El nombre conserva las comas posteriores. Cabeceras inline y EXTVLCOPT mantienen su comportamiento y tests. Una línea individual extraordinariamente larga todavía puede demorar una comprobación hasta terminar `readLine`; no se introdujo un límite que rechazara listas existentes.

### XMLTV

Offsets RFC822 y extensión con dos puntos se normalizan al formato numérico que corresponde al patrón `Z`; fechas malformadas o intervalos invertidos no generan programas. Sin offset se usa UTC, según el [DTD oficial XMLTV](https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd), en lugar de depender de la zona del dispositivo. El muestreo DST con offsets explícitos está cubierto en JVM.

El parser consulta cancelación durante recorrido y etiquetas anidadas, y rechaza fin de documento inesperado dentro de una etiqueta. La construcción de intereses EPG se realiza fuera de Main. El test Android preparado permite verificar el mismo parseo con libcore de API 24 y dispositivos actuales; **compilarlo no equivale a ejecutarlo**.

### Propiedad de trabajos y publicación de resultados

- `LatestTask`: un job y generación por familia (carga de catálogo, carga EPG y explorador). Cancelar invalida incluso resultados de una operación que ignore cancelación. La publicación comprueba generación y contexto activo.
- `MainViewModel`: separa descarga EPG de ticker; sustituir lista, desactivar EPG, restablecer o terminar sesión cancela las operaciones correspondientes. Cerrar/cambiar de explorador invalida sus resultados para que no vuelva a abrirse por una respuesta tardía.
- La publicación del catálogo revalida favoritos, grupos y query tras preparación, evitando aplicar un snapshot editable obsoleto. Puede repetir preparación si esos datos cambian durante la operación; es un coste de corrección pendiente de medir y aislar en fase 3, no una mejora de rendimiento prometida.
- Un filtro captura su índice de entrada antes de trabajar en Default, comprueba lista/query/grupos/favoritos antes de publicar y resuelve visibilidad usando la selección **vigente**, sin restaurar el canal del snapshot antiguo.
- Lectores y respuestas conservan `use`. HTTP usa un puente de callback OkHttp cancelable que mantiene `Call.cancel` conectado mientras se consume el cuerpo, no solo hasta recibir headers. El parseo remoto sigue siendo incremental, fuera de Main; trabaja en el callback de OkHttp, cuyo dispatcher limita concurrencia. No se cambian timeouts ni servidores.
- Keep-alive usa `delay` cancelable en lugar de dormir un hilo y ya no captura `Throwable`. La cancelación no se convierte en un mensaje de error de carga. Los loops de preparación/índices/filtro tienen comprobaciones por lotes; los listados de archivos también consultan cancelación.

Los tests verifican el componente usado por el ViewModel y la reconciliación de selección de forma aislada. **No hay un test de integración completo de MainViewModel con Android Context/SharedPreferences ni se ha ejecutado un caso de UI real con cargas concurrentes**; no atribuirles una cobertura mayor de la que tienen.

### Lint y despacho del mando

- Se reemplaza la propagación de `@UnstableApi` por opt-in explícito `androidx.annotation.OptIn` en los límites que consumen Media3. No se deshabilita `UnsafeOptInUsageError` globalmente.
- Se elimina el override de la API heredada restringida de AndroidX Core. Un [Window.Callback público](https://developer.android.com/reference/android/view/Window.Callback.html) intercepta las mismas acciones y delega todos los eventos restantes al callback original. No procesa flechas como cambio de canal.
- El cambio de enrutado requiere prueba real de mando, teclado, ratón, edición numérica y Atrás; unit tests no acreditan su compatibilidad con todos los dispositivos.

## Archivos modificados o añadidos

Rutas relativas a la raíz del repositorio; cada cambio permanece revisable sin commit:

| Archivos | Cambio |
|---|---|
| `.gitignore`, `.github/workflows/ci.yml` | Tests versionables y checks obligatorios |
| `app/build.gradle.kts` | Dependencia JVM de test XMLTV |
| `M3uParser.kt`, `XmltvParser.kt` | Correcciones localizadas y cancelación |
| `PlaylistLoader.kt`, `PlaylistRepository.kt`, `EpgRepository.kt`, `FileBrowserRepository.kt` | Lectura cancelable y trabajo fuera de Main |
| `LatestTask.kt` (nuevo), `MainViewModel.kt`, `Models.kt` | Jobs/generaciones, preparación y selección vigente |
| `PlaybackCache.kt`, `PlayerSurface.kt` | Opt-in local Media3; PlayerSurface además fija contrato get.php |
| `AppScreens.kt` | Opt-in local y sondas SideEffect/foco sin rediseño |
| `MainActivity.kt`, `NavigationDiagnostics.kt` (nuevo) | Window.Callback público y diagnóstico debug optativo |
| `ui/SettingsScreen.kt`, `ui/components/PlayerComponents.kt` | Opt-in en copias de UI también compiladas |
| `app/src/test/java/com/toigo/miptvga/*.kt` | Cuatro fuentes de tests anteriores y tres nuevas, ahora visibles a Git |
| `app/src/androidTest/java/com/toigo/miptvga/XmltvAndroidTest.kt` | Test de offsets preparado para dispositivo |
| `docs/fase1/` | Resultados, instrumentación y requisito de Salir |

Las rutas Kotlin abreviadas de la tabla están bajo `app/src/main/java/com/toigo/miptvga/`.

## Tests: desglose de 47

| Suite | Casos | Cobertura |
|---|---:|---|
| M3uParserTest | 13 | Diez anteriores; coma en nombre/atributos, BOM/vacíos/incompleta y cancelación incremental |
| PlaybackUrlHeuristicsTest | 16 | Quince anteriores, incluidos los tres fallidos; clasificación explícita vs ambigua con recuperación |
| FavoritesBehaviorTest | 3 | Comportamiento previo de favoritos canal/grupo |
| GroupAggregationTest | 2 | Normalización de grupos y favoritos virtuales |
| XmltvParserTest | 7 | Offsets positivos/negativos/UTC/colon, ausencia de zona, DST, entradas inválidas, cancelación y XML truncado |
| LatestTaskTest | 4 | Resultado antiguo no cooperativo, invalidación al restablecer incluso en NonCancellable, propagación de cancelación y selección actual al publicar filtro |
| PlaylistLoaderTest | 2 | Servidor HTTP loopback: socket cerrado al cancelar durante body y lectura UTF-8 normal con nombre con coma |

No se accedió a servicios IPTV externos. Los sockets de test son `127.0.0.1` efímeros y las URLs de canales usan dominios de ejemplo. No se probaron credenciales privadas.

## Avisos de Lint conservados y por qué

| Categoría | Cantidad | Decisión |
|---|---:|---|
| GradleDependency / NewerVersionAvailable / AndroidGradlePluginVersion | 16 / 3 / 1 | Sin actualización masiva de dependencias; migrar por separado con compatibilidad verificada |
| OldTargetApi | 1 | Mantener SDK 35 durante saneamiento; no confundir aviso con cambio autorizado de compatibilidad |
| InlinedApi | 6 | Constantes de ratón/permisos de SDK nuevos; inspeccionar guardas y probar dispositivos, no inferir crash por una constante inline |
| UseKtx / ObsoleteSdkInt | 6 / 2 | Cambios de estilo/guardas no necesarios para los bugs de fase 1 |
| DefaultLocale | 4 | Formateo de tiempo existente; revisar con localización sin mezclarlo con la corrección de offsets XMLTV |
| ClickableViewAccessibility | 2 | Táctil sobre vistas nativas del player; probar click/performClick y controles accesibles con ciclo de vida en fase 4/5 |
| UnusedResources | 5 | No borrar recursos de identidad o compatibilidad sin comprobar todas las variantes |
| IconLauncherShape / IconXmlAndPng / IconLocation | 5 / 1 / 1 | Recursos gráficos existentes, sin rediseño en fase 1 |
| ChromeOsAbiSupport | 1 | ARMv7/ARM64 conservados; no añadir ABI para silenciar aviso |
| Overdraw | 2 | Fondos del player: medir antes de retirar protección contra frames vacíos |

Total: **56 avisos**, todos visibles; no existe baseline ni exclusión general de Lint. CI falla ante errores nuevos. Tampoco se certifica ausencia de CVE por pasar Lint.

## Comparación JVM y límites

Se ejecutó el mismo harness con copias de los parsers/modelos de `8a2e87f` y con fase 1. Fuentes y scripts: [comparar_listas.py](comparar_listas.py), [base](benchmark_base_jvm.txt), [fase 1](benchmark_fase1_jvm.txt). Entorno de escritorio descrito en la auditoría; procesos separados, mismo generador, cinco tamaños y límite `-Xmx1536m`.

| Canales | Parseo base/fase1 ms | Preparación base/fase1 ms | Heap indexado base/fase1 MiB |
|---:|---:|---:|---:|
| 1.000 | 5,03 / 5,41 | 41,46 / 34,34 | 0,07 / 0,04 (ruido) |
| 10.000 | 26,95 / 23,57 | 70,29 / 52,59 | 4,12 / 4,09 |
| 100.000 | 170,08 / 131,57 | 238,44 / 234,98 | 44,97 / 44,96 |
| 500.000 | 509,85 / 616,98 | 1.275,15 / 692,30 | 224,64 / 224,50 |
| 1.000.000 | 988,64 / 861,96 | 9.282,33 / 688,93 | 449,03 / 449,20 |

**Una muestra por configuración, sin aislamiento de la compilación y carga del escritorio: no demuestra aceleración ni ausencia de regresión de rendimiento.** La preparación base de 1M es un outlier evidente respecto a la auditoría anterior; no atribuir esa diferencia a cambios algorítmicos. En 500k el parseo de fase1 fue más lento. Se conservan ambos resultados sin seleccionar solo los favorables. La retención sigue aproximadamente igual; no se optimizó el catálogo.

Los casos reproducibles sí confirman el nombre completo con coma y que XMLTV con offsets pasa de cero timelines a dos claves de un programa (id/nombre) en JVM. No se cambiaron los grupos sin comillas ni la aceptación de líneas de URL desconocidas, que siguen como limitaciones previas.

## Pendientes y próximo paso concreto

- [Instrumentación y plan de comparación](INSTRUMENTACION_NAVEGACION.md) listos para ejecutar en ARM Android TV modesto. No hay resultados de DPAD, jank o PSS Android todavía.
- Tras autorización de fase 2: tomar línea base con 100/1k/10k/100k/1M según memoria, aislar `SidePanel.firstOrNull`, estado Compose y mapas globales. Cambiar una sola operación y comparar mismos fixtures/trazas. Las observaciones del usuario de 2.500.000 y 150.000 **líneas** se registran como síntomas de dispositivo, no se convierten a canales sin contar EXTINF/entradas reales.
- Siguen pendientes de sus fases: snapshots de listeners/sesiones de reproductor, presupuestos de reconexión, caché coordinada, reproducción entre auxiliares/fullscreen, autoplay/restauración independiente, seguridad/redacción de URLs y nueva UI A.
- [Salir: requisito completo y aceptación](SALIR_REQUISITO.md). No se ha implementado la nueva acción visible. La salida existente todavía borra caché mediante `releaseAndClear`; es incompatible con el nuevo requisito y debe corregirse al implementar el cierre coordinado. No considerarlo entregado por añadir cancelación al ViewModel.
- Sin test integral ViewModel/Context/persistencia y sin dispositivo real: no afirmar ausencia de fugas, audio residual, sesiones obsoletas o regresiones de input.

**Entrega limitada a fase 1 más preparación de instrumentación. Esperar autorización antes de comenzar la fase 2 o adelantar la nueva interfaz.**
