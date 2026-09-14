# Política de seguridad

## Versiones con soporte

| Versión | Soportada |
| ------- | --------- |
| 1.1.x   | ✅        |
| < 1.1   | ❌        |

## Alcance

**miptvga** es una aplicación **Android** (Kotlin + Jetpack Compose) para reproducir listas IPTV M3U/M3U8 en TV, cajas Android y móviles, con backends **LibVLC** y **Media3 ExoPlayer**. En el ámbito de seguridad nos interesa especialmente:

- **Credenciales en listas y EPG**: URLs de paneles Xtream (`username`, `password`, tokens) en M3U, XMLTV o ajustes guardados en el dispositivo.
- **Almacenamiento local**: preferencias (última lista, favoritos, EPG, keep-alive), cachés de reproducción/imágenes y permisos de almacenamiento del explorador de archivos.
- **Reproducción y red**: manejo de streams remotos, reconexión, keep-alive Xtream y cierre limpio de conexiones al hacer zapping.
- **Dependencias**: vulnerabilidades en OkHttp, Coil, LibVLC, Media3 u otras librerías del módulo `app`.

**Fuera de alcance habitual:**

- Disponibilidad o legalidad de listas IPTV y de los servidores de terceros.
- Fallos del panel Xtream, del proveedor o de la red ajenos a esta aplicación.
- Contenido de las listas que el usuario carga voluntariamente.

## Cómo reportar una vulnerabilidad

1. **No** abras un issue público con detalles del fallo ni pegues URLs con usuario/contraseña, tokens o capturas con credenciales.
2. Usa [GitHub Security Advisories](https://github.com/entreunosyceros/miptvga/security/advisories/new) (**Report a vulnerability**) si tienes acceso.
3. Si no puedes usar Advisories, abre un issue con título `SECURITY (sin detalles)` y pide un canal privado; no incluyas pasos de explotación en público.

Incluye, en la medida de lo posible:

- Descripción del problema y componente afectado (`PlayerSurface`, `PlaylistLoader`, `XtreamSession`, EPG, almacenamiento, etc.).
- Pasos para reproducirlo (sin credenciales reales).
- Impacto estimado (credenciales, datos locales, ejecución de código, red).
- Versión de la app (`versionName` / `versionCode` en `app/build.gradle.kts`, p. ej. 1.1 / 2) o commit afectado.
- Dispositivo o emulador, versión de Android y backend de reproducción (VLC / ExoPlayer).
- Sugerencia de mitigación, si la tienes.

## Qué esperar

- **Acuse de recibo** en un plazo razonable (habitualmente en pocos días).
- Evaluación del informe y, si procede, parche o mitigación en una versión posterior.
- Crédito al informante en las notas de la corrección, salvo que prefiera anonimato.

## Buenas prácticas para usuarios

- No subas listas M3U con usuario/contraseña, capturas con URLs sensibles ni keystores a issues, PRs o Releases públicas de terceros.
- Descarga el APK solo desde el repositorio oficial: [github.com/entreunosyceros/miptvga](https://github.com/entreunosyceros/miptvga) → [Releases](https://github.com/entreunosyceros/miptvga/releases).
- En dispositivos compartidos, ten en cuenta que la app puede recordar la última lista, favoritos y ajustes EPG/Xtream.
- Uso destinado a listas IPTV a las que tengas acceso legítimo.
