# Guía de contribución

¡Gracias por interesarte en **[miptvga](https://github.com/entreunosyceros/miptvga)**! Es una aplicación IPTV para **Android TV**, cajas Android, tablets y móviles (Kotlin + Jetpack Compose) orientada a listas M3U grandes, con reproducción VLC/ExoPlayer y soporte Xtream. Cualquier mejora bien planteada es bienvenida.

## Antes de empezar

- Lee el [README](README.md) para entender el alcance del proyecto.
- Revisa las [issues abiertas](https://github.com/entreunosyceros/miptvga/issues) por si alguien ya trabaja en lo mismo.
- Para el comportamiento en la comunidad, consulta el [Código de conducta](CODE_OF_CONDUCT.md).
- Para vulnerabilidades, sigue [SECURITY.md](SECURITY.md) (no abras issues públicas con detalles de explotación).

## Cómo puedes ayudar

- **Reportar errores** con pasos claros (dispositivo, Android TV/móvil, backend VLC o ExoPlayer, versión de la app).
- **Proponer mejoras** explicando el problema que resuelven.
- **Enviar pull requests** con cambios acotados y probados.
- **Mejorar documentación** (README, plantillas de GitHub, comentarios útiles en el código).

## Entorno de desarrollo

Requisitos:

- **JDK 17** o superior
- Android SDK (`compileSdk` / `targetSdk` 35, `minSdk` 24)
- Dispositivo o emulador Android TV / Android compatible
- Gradle Wrapper del repositorio

```bash
git clone https://github.com/entreunosyceros/miptvga.git
cd miptvga
```

Crea `local.properties` (no se sube al repo) con tu SDK:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

### Compilar

```bash
./gradlew assembleDebug
```

APK de salida:

```text
app/build/outputs/apk/debug/miptvga.apk
```

### Tests unitarios

```bash
./gradlew testDebugUnitTest
```

### Release (opcional, firma local)

Si tienes `keystore.properties` y un keystore en `keystore/` (ambos ignorados por git):

```bash
./gradlew assembleRelease
```

Salida: `app/build/outputs/apk/release/miptvga.apk`.

## Estructura del código

| Ruta | Contenido |
|------|-----------|
| `app/src/main/java/com/toigo/miptvga/` | Lógica principal: pantallas, ViewModel, parsers, player |
| `AdaptiveLayout.kt` | Métricas y layouts adaptativos (móvil / tablet / TV) |
| `AppScreens.kt` | UI principal (Compose) |
| `PlayerSurface.kt` | Superficies VLC y ExoPlayer, zapping y reconexión |
| `MainViewModel.kt` | Estado de UI, filtros por grupo, favoritos, EPG, Xtream keep-alive |
| `M3uParser.kt` / `PlaylistLoader.kt` | Parseo y carga de listas M3U/M3U8 |
| `XtreamSession.kt` | Detección Xtream y keep-alive |
| `XmltvParser.kt` / `EpgRepository.kt` | Guía EPG / XMLTV |
| `ui/` | Pantallas y componentes auxiliares |
| `app/src/main/res/` | Iconos, logo, banner TV, temas |
| `.github/` | CI, plantillas de issues y pull requests |

## Estilo de código

- Sigue el estilo del código existente (Kotlin, Compose, nombres e imports).
- Cambios **mínimos y enfocados**: no mezcles varias funcionalidades en un mismo PR.
- Los textos visibles para el usuario van en **español**.
- No incluyas secretos, `local.properties`, `keystore.properties`, keystores ni listas M3U con usuario/contraseña.
- No pegues en issues/PRs URLs de IPTV, EPG o Xtream con credenciales; anonimiza capturas.
- No subas APKs al árbol del código (`*.apk` está en `.gitignore`); se publican en [Releases](https://github.com/entreunosyceros/miptvga/releases).

## Pull requests

1. Crea una rama descriptiva desde `main` (por ejemplo `fix/channel-zapping` o `feat/adaptive-guide`).
2. Describe **qué** cambias y **por qué**.
3. Indica cómo lo has probado (dispositivo/emulador, backend VLC/ExoPlayer, pasos manuales).
4. Si tocas reproducción IPTV/Xtream o EPG, no pegues URLs reales con credenciales.
5. Actualiza el README solo si el cambio lo requiere.

Usa la [plantilla de pull request](.github/pull_request_template.md) al abrir el PR.

## Reportar problemas de seguridad

No abras issues públicas para vulnerabilidades. Sigue la [política de seguridad](SECURITY.md).

## Licencia

Al contribuir, aceptas que tu aportación se publique bajo la misma licencia del repositorio.
