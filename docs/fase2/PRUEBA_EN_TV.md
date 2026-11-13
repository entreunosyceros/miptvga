# Prueba sencilla en Android TV

No se ha ejecutado esta prueba aquí: `adb devices -l` no muestra dispositivos. Objetivo: separar **entrada al grupo**, **primera pulsación**, **siguientes pulsaciones**, **callback de foco** y **frames**. La aplicación estable y el sistema Android TV antiguo pueden tener costes de arranque/JIT/GC propios; no atribuirlos todos al catálogo.

## Preparación (una vez)

1. Compilar/instalar el debug de esta carpeta con el mismo método que utilizas habitualmente. No hay una release nueva publicada. `adb install -r app/build/outputs/apk/debug/miptvga.apk` conserva los datos cuando la firma instalada coincide; si la firma difiere, no desinstales para resolverlo sin guardar antes tus datos. No se cambia la firma del proyecto.
2. Cerrar la aplicación anterior para no abrir dos Activities. Iniciar el debug con diagnóstico explícito:

   ```bash
   adb shell am start -n com.toigo.miptvga/.MainActivity --ez diagnostic_navigation true
   ```

   No hace falta root. En un build release el extra se ignora. Abrir normalmente sin el extra vuelve a la instrumentación desactivada. Si Android ya tiene la Activity abierta puede entregar el extra sin llamar a onCreate y no activarla; cerrarla primero.
3. Para comparar solo catálogo/foco, desactivar temporalmente logos y EPG en Ajustes y dejar el mismo backend. No tocar buffers. Mantener el mismo estado en todas las repeticiones y anotar después cómo estaban tus ajustes.
4. Opcionalmente generar listas sintéticas locales (no sobrescribe archivos existentes):

   ```bash
   python3 docs/fase2/crear_lista.py --channels 100 --out navegacion_100.m3u
   python3 docs/fase2/crear_lista.py --channels 1000 --out navegacion_1000.m3u
   adb push navegacion_100.m3u /sdcard/Download/
   adb push navegacion_1000.m3u /sdcard/Download/
   ```

   Cargarlas desde el explorador habitual. Al cargar otra fuente, la app recordará esa fuente como última lista; conserva la forma de volver a tu lista habitual. No se borran favoritos ni configuraciones por estas herramientas. Las fixtures usan un archivo multimedia inexistente, sin red, logos ni EPG: la app puede mostrar un error inicial de reproducción; esperar a que termine y no pulsar OK sobre canales durante la medición. Esto prueba navegación **sin vídeo activo**, no reproducción.

El generador admite 100/1k/10k/100k/1M, dos líneas por canal más cabecera. Cada fixture lleva hasta 100 grupos y al menos unos diez canales por grupo pequeño para los tamaños iniciales. **No empezar por 1M en la TV antigua**: detener el escalado si carga, memoria o respuesta no son aceptables. Las referencias reales de 150.000 y 2.500.000 líneas no se interpretan como canales; el log informa del recuento real del catálogo/filtro.

## Captura de dos minutos

Con el catálogo ya cargado y mientras la app sigue abierta:

```bash
python3 docs/fase2/capturar_tv.py --seconds 120 --out tv_1000.txt
```

Usar `--adb /ruta/a/adb` si no está en PATH y `--serial IDENTIFICADOR` si hay varios dispositivos. El script solo consulta modelo/API/ABI y captura el tag de diagnóstico; **no instala, inicia, cierra ni modifica la app, no limpia logcat y no recoge el log general**. Ctrl+C termina antes la captura. Cada archivo debe contener una sola apertura de la app; no concatenar sesiones.

Durante la captura:

1. Entrar en un grupo pequeño, dejar que se vea, esperar 2 s.
2. Con foco en la lista, pulsar **Abajo una vez**, esperar 1 s; después Abajo nueve veces, una por segundo. No mantener pulsado. Si el foco empieza en un extremo sin vecino, usar la otra dirección y anotarlo.
3. Entrar en «Todos», esperar 2 s y repetir. No pulsar OK en canales ni mezclar ratón mientras se miden flechas.
4. Repetir grupo/«Todos» al menos tres veces; en otra captura repetir pulsando la primera flecha **en cuanto aparezca la lista**, para detectar trabajo pendiente del primer frame. Anotar «espera 2 s» o «inmediata» en el nombre del archivo.
5. Para distinguir inicio de proceso y reentrada, hacer otra captura tras cerrar/reabrir normalmente la app con el extra, sin reiniciar Android. No mezclar ambos casos en el mismo archivo.

«Primera» significa primer DOWN arriba/abajo observado desde que se presentó ese catálogo, no la primera tecla de cualquier clase. Las teclas destinadas a otros controles y las que no cambian foco no producen necesariamente muestra de fila. Si una pulsación se queda esperando otra, el timestamp pendiente puede ser sustituido: conservar el log crudo, observar muestras ausentes y repetir con pausas más largas; no tratarlas como cero milisegundos.

Para comparar reproducción/EPG/logos, realizar capturas separadas después, manteniendo fija la lista y anotando cada variable. No usar fixtures de archivo inexistente como prueba de vídeo.

## Obtener el resumen y devolver resultados

```bash
python3 docs/fase2/resumir_tv.py tv_1000.txt > tv_1000_resumen.csv
```

Guardar el TXT y CSV, junto con esta nota breve:

```text
Lista: sintética / habitual (sin enviar contenido ni URL)
Canales en Todos y en el grupo: [recuento del log]
TV / Android / RAM si se conoce:
Espera antes de la primera flecha: 2 s / inmediata
Vídeo activo: sí/no. Logos: sí/no. EPG: sí/no
Pausa inicial: igual / menor / mayor / no aparece
Pulsaciones siguientes: normales / con pausas
¿El foco empezó ya en una fila?: sí/no
```

Los mensajes propios solo contienen números, tamaños y «Todos/otro grupo», nunca nombres, URLs ni teclas de texto. El archivo añade modelo/API/ABI; se pueden retirar esas cabeceras si no se desean compartir. No enviar listas privadas ni un logcat completo.

## Interpretación y límites

- `work=filter.sync`: tiempo de preparar el resultado al cambiar grupo, en Main. `filter.async` es la búsqueda/favoritos en Default. No es tiempo de renderizado.
- `dispatchNs`: procesamiento de la tecla; `mainCpuNs` separa CPU del tiempo de espera dentro del despacho. `keyEventAgeMs` incluye la espera anterior a recibirla y el despacho.
- `dispatchToFocusCallbackNs`: hasta notificación de foco de una fila, que puede ocurrir durante o después del despacho. No mide cuándo lo ve el usuario.
- `dispatchToFrameCallbackNs`: primer callback FrameMetrics cuyo intended-vsync es posterior al foco, con el mismo event/segment/ordinal. Es una **aproximación posterior al foco**, con scheduling y entrega del callback incluidos, no medida de presentación de píxeles ni garantía de que ese frame contenga el borde actualizado. No restar estos tiempos para inventar latencia GPU.
- El resumen presenta n, mediana, p95 por rango más cercano y máximo, separando Todos/grupo, tamaño y primera/siguientes. Las métricas tienen números de muestras distintos si se solapan eventos o faltan callbacks.
- `frames/overBudget`, GC, heap y PSS son muestras agregadas cada ~10 s. Un intervalo puede incluir ambos grupos; para atribuirlo, repetir una captura dedicando al menos 20 s a cada grupo y relacionarla con sus timestamps. `callbackDrops` conserva el valor del último callback, no suma todo el intervalo.
- Para resolver el frame visual exacto, hace falta System Trace/Perfetto con input, UI/render y secciones `miptvga.*`, o grabación externa controlada. Esto queda para el contraste con hardware; no se deriva de JVM.
- Sin un APK/traza de fase 1 guardados, la comparación Android antes/después sigue pendiente. La comparación JVM adjunta sí usa el método de fase 1 guardado; **no reconstruye ni valida la UI antigua**. No sustituir una versión instalada de firma distinta ni borrar datos para intentar esa comparación.
