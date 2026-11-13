# Requisito adicional aprobado: salida explícita

Este documento incorpora el requisito a miptvga 2.0. **La nueva opción de navegación y el diálogo todavía no están implementados**, porque esta ejecución está limitada a fase 1 e instrumentación preparatoria, sin nueva interfaz. No considerar validada la salida multimedia por superar los tests JVM de cancelación.

## Integración prevista

- Última acción del rail de variante A: icono de apagado y etiqueta «Salir», con foco visible y activación por OK, clic y toque.
- Diálogo modal con «Cancelar» inicialmente enfocado y «Salir»; Atrás equivale a Cancelar. Al cancelar, restaurar foco en Salir y mantener la reproducción.
- Una única solicitud de cierre idempotente, diferente de Atrás contextual y de minimizar. Deshabilitar confirmaciones repetidas mientras se libera la sesión.
- Tras confirmar: impedir nuevas cargas/callbacks, invalidar generaciones, cancelar carga de catálogo/explorador, filtro, descarga/ticker EPG, keep-alive y reconexión; detener el motor activo, retirar listeners, desvincular superficie y liberar motor una vez.
- Guardar el estado persistente previsto, esperar las escrituras que sean necesarias y finalizar Activity mediante la API de Android. Nunca `System.exit`, `exitProcess`, matar proceso ni bucles de espera en Main.
- Conservar favoritos, fuente/lista guardada, ajustes y cachés persistentes. El próximo arranque debe crear un motor nuevo y recuperar datos persistidos, según el ajuste autoplay aprobado para su fase.

## Trabajo ya preparado y deuda restante

Fase 1 ha añadido invalidación/cancelación común de tareas del ViewModel en restablecimiento, salida de sesión existente y onCleared, y cancelación HTTP conectada al socket mientras se lee el cuerpo. Esto sirve de base al cierre.

La versión actual ya tiene un botón Salir dentro de Ajustes. No es la opción solicitada del rail y no satisface el requisito completo: `MainActivity.onDestroy` y el cierre existente llaman `PlaybackCache.releaseAndClear`, que borra caché persistente. Se debe separar **release** de **clear**, coordinar los consumidores del caché y eliminar ese borrado en el cierre normal antes de dar por terminada esta funcionalidad. No ocultar esta incompatibilidad como si ya estuviera resuelta.

Los reintentos/superficies aún pertenecen a los Composables; el cierre determinista necesita la propiedad estable de sesión prevista en fase 4. Incorporar la acción visible y su diálogo en fase 5, después de ese cambio, o en una entrega específica de salida si el usuario la autoriza antes. No introducirla aprovechando esta fase de saneamiento.

## Aceptación posterior en Android

Probar en VLC y ExoPlayer, SurfaceView y TextureView, con stream local sintético:

1. Cancelar con botón y Atrás: foco restaurado, ningún cambio de stream ni borrado de datos.
2. Confirmar durante reproducción, buffering, reintento, carga M3U y descarga EPG.
3. Verificar ausencia de audio/vídeo, sockets activos y reconexiones después de terminar Activity; observar logs/servidor y recursos del motor, sin depender solo de la pantalla.
4. Reabrir y comprobar favoritos, fuente y configuración intactos; caché sin borrado por salida; nuevo identificador de sesión/motor.
5. Repetir varias veces y probar pulsaciones/clics duplicados en Confirmar.

Estado: especificado, pendiente de implementación y de dispositivo real.
