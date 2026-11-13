# Preparación de la fase 2: medición Android, todavía no optimización

La instrumentación existe, pero **no se ha ejecutado en Android**. Ningún dispositivo está conectado. Los tiempos JVM adjuntos no representan mando, ART, GPU ni reproducción.

## Activación y alcance

Solo se activa en una aplicación debuggable cuando su Activity se inicia con el extra explícito `diagnostic_navigation=true`. Release ignora el extra. Cerrarla y abrirla normalmente desactiva la instrumentación. No añade menús, servicios, telemetría, dependencias ni preferencias persistidas.

Con el APK debug instalado y sin otra instancia de la aplicación abierta:

```bash
adb shell am start -n com.toigo.miptvga/.MainActivity --ez diagnostic_navigation true
adb logcat -s MiptvgaNav:D StrictMode:D
```

No instalar ni iniciar estos comandos automáticamente en un dispositivo del usuario. Antes de recoger trazas, cargar fixtures sintéticas locales, sin URLs privadas: StrictMode/Perfetto y las bibliotecas del sistema pueden incluir datos ajenos a los mensajes de diagnóstico propios.

`NavigationDiagnostics` registra únicamente números agregados, sin nombres de canales, queries, credenciales, cabeceras, paths ni URLs. No registra todas las teclas: solo despacho DOWN de DPAD arriba/abajo. El Window.Callback delegado conserva el callback original para eventos no consumidos; debe validarse en mando/teclado reales antes de publicar.

## Qué mide y qué no

| Campo o evento | Interpretación |
|---|---|
| `miptvga.dpad.dispatch` | Sección Trace alrededor del enrutado de un DOWN arriba/abajo |
| `dispatchNs` | Duración de ese enrutado hasta devolver el control; no hasta el frame visible |
| `mainCpuNs` | CPU del hilo principal durante ese despacho |
| `keyEventAgeMs` | Edad de `KeyEvent.eventTime` al terminar; incluye espera previa y despacho, usando uptime |
| `repeat` | Repeticiones del evento Android; detectar pulsaciones acumuladas |
| `miptvga.channel.focus`, `dispatchToFocusCallbackNs` | Último DOWN observado → callback de foco de una fila activa; aproximación a actualización de foco, **no latencia hasta píxel** |
| `frames`, `avgNs`, `maxNs`, `overBudget` | TOTAL_DURATION de FrameMetrics en ventanas de unos 10 s; presupuesto según Hz de la pantalla. Over-budget no sustituye una clasificación completa de jank |
| `callbackDrops` | Frames descartados comunicados por el último callback del intervalo, no suma total |
| `rowCompositions`, `panelCompositions` | Ejecuciones SideEffect completadas de filas/cabecera lateral de la ruta activa; cuentan composición inicial y recomposición, no solo invalidaciones |
| `heapBytes`, `pssKiB` | Heap utilizado y PSS del proceso, muestreados en el hilo de métricas |
| `gcCount`, `gcTimeMs`, `allocatedBytes` | Contadores acumulados ART; calcular diferencias entre muestras; pueden faltar según runtime/vendor |
| StrictMode | Lecturas/escrituras de disco y red en Main; no detecta por sí solo algoritmos CPU O(N) |

Los callbacks FrameMetrics y el muestreo de memoria trabajan en HandlerThread y se retiran al destruir la Activity. La política StrictMode anterior se restaura. La instrumentación está desactivada por defecto y también añade coste cuando se activa: comparar con trazas sin logs antes de concluir rendimiento final.

Si una tecla no cambia foco (borde de lista o evento consumido), no habrá muestra de foco. El siguiente DOWN sustituye el timestamp pendiente. Una transición ajena al mando antes de otro DOWN puede contaminar una muestra; segmentar la sesión y no mezclar ratón/diálogos con pruebas de desplazamiento. Las filas solo se cuentan mientras se componen, nunca se recorre todo el catálogo para instrumentar.

## Captura propuesta en hardware

1. Registrar modelo, SoC/ABI ARM, Android/API, RAM, memoryClass/lowRam, resolución/Hz, backend, versión/hash de fuentes, logos/EPG activos y estado térmico.
2. Usar catálogos de **100, 1.000, 10.000, 100.000 y 1.000.000 canales**. Medir líneas y canales por separado. El Reader sintético de `docs/auditoria/medir_listas.py` genera dos líneas por canal, pero los archivos reales pueden tener comentarios/atributos adicionales. Empezar por 100 y detener escalado antes de agotar la memoria.
3. Repetir arriba/abajo con cadencia conocida en el mismo grupo, sin reproducir y con reproducción. Después repetir con logos y EPG por separado. No cambiar todas las variables simultáneamente.
4. Android Studio System Trace/Perfetto: habilitar categorías `gfx`, `view`, `input`, `sched`, `freq`, `dalvik` y las secciones de esta aplicación. Correlacionar evento de input, callback de foco y presentación del frame. Guardar traza original y parámetros de captura.
5. Allocation Recording/Memory Profiler en una ejecución separada para localizar asignaciones; comparar heap dumps/PSS y GC. No atribuir pausas al parser solo porque la lista sea grande. El profiler puede alterar la latencia.
6. Calcular p50/p95 y peor caso de al menos 100 cambios de foco válidos por configuración; separar repetición mantenida y pulsaciones discretas. Conservar también muestras descartadas y motivo.

No se ha cambiado la ruta de búsqueda lineal `SidePanel.firstOrNull`, ni la creación de mapas O(N), ni la navegación de LazyColumn en esta fase: son objetivos de comparación para la fase 2. Primer cambio propuesto tras autorización: medir la línea base instrumentada, sustituir exclusivamente el lookup de la entrada seleccionada por acceso directo validado al índice original y comparar la misma sesión. Luego aislar propagación de estado y mapas, un cambio por comparación. No tocar buffers ni reemplazar LazyColumn sin evidencia.
