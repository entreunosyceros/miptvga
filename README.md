# miptvga

<div align="center">
<img src="app/src/main/res/drawable/miptvga.png" alt="miptvga" width="280" />

**miptvga** · Make IPTV Great Again
</div>

Aplicación IPTV para **Android TV**, cajas Android, tablets y móviles. Carga listas **M3U** (URL o archivo local), organiza miles de canales, reproduce con **VLC** o **ExoPlayer** y se adapta al tamaño de pantalla.

Versión actual: **1.1** (`versionCode` 2).

## Descargar APK

El APK se publica en [**Releases**](https://github.com/entreunosyceros/miptvga/releases) de este repositorio (no va dentro del código fuente; `*.apk` está en `.gitignore`).

1. Abre la [última release](https://github.com/entreunosyceros/miptvga/releases/latest).
2. Descarga **`miptvga.apk`**.
3. Instálalo en el televisor, caja Android o dispositivo (permite orígenes desconocidos si hace falta).

También puedes compilarlo tú mismo (ver [Compilación](#compilación)).

## Qué hay de nuevo en 1.1

- Interfaz **adaptable** a pantallas compactas, medianas y TV (panel apilado o lateral).
- Mejor soporte **Xtream Codes**: keep-alive configurable y reconexión más robusta.
- Reconexión automática en streams HTTP(S) y listas remotas.
- Panel lateral simplificado (acciones arriba, grupos y canales más claros).
- Explorador interno de archivos para M3U/M3U8.
- Pantallas de búsqueda, guía EPG, info de canal, ajustes y about.
- Icono y recursos visuales actualizados.
- Salida de build unificada como **`miptvga.apk`**.

## Características

- Carga listas **M3U / M3U8** por URL o archivo local.
- Navegación por **grupos**, **búsqueda** y **favoritos** (canal y grupo).
- **Guía EPG / XMLTV** opcional.
- Reproducción con **LibVLC** o **Media3 ExoPlayer**.
- Modo de compatibilidad de vídeo.
- Fullscreen con controles OSD (play/pausa, seek / timeshift en live cuando aplica).
- Recuerda la última lista cargada.
- Pensada para **Leanback** (Android TV) y mando a distancia, usable también en móvil/tablet.

## Stack técnico

| Pieza | Detalle |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| SDK | compile/target **35**, minSdk **24** |
| Reproductores | VLC (`libvlc-all`) · Media3 ExoPlayer |
| Red | OkHttp · monitor de red |
| Imágenes | Coil |

## Requisitos para compilar

- **JDK 17** o superior
- Android SDK local
- Dispositivo/emulador Android TV o Android compatible
- Gradle Wrapper del proyecto

## Estructura del proyecto

```text
miptvga/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/toigo/miptvga/   # pantallas, player, parsers, Xtream…
│       │   ├── AdaptiveLayout.kt    # métricas por tamaño de pantalla
│       │   ├── AppScreens.kt        # UI principal
│       │   └── ui/                  # pantallas auxiliares / componentes
│       └── res/                     # iconos, logo, banner TV, temas
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── keystore.properties.example
├── README.md
└── .gitignore
```

## Configuración local

### SDK de Android

`local.properties` es local y **no** se sube al repo:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

### Firma release (opcional)

Para un `release` firmado usa `keystore.properties` y un keystore en `keystore/` (ambos ignorados por git). Parte de `keystore.properties.example`.

## Compilación

### APK debug

```bash
./gradlew assembleDebug
```

Salida:

```text
app/build/outputs/apk/debug/miptvga.apk
```

(`assembleDebug` copia/renombra automáticamente a `miptvga.apk`.)

### Tests

```bash
./gradlew testDebugUnitTest
```

### APK release

```bash
./gradlew assembleRelease
```

Salida:

```text
app/build/outputs/apk/release/miptvga.apk
```

### Bundle (Play Store)

```bash
./gradlew bundleRelease
```

## Publicar el APK en GitHub

Recomendado: **GitHub Releases** (no commits del binario).

1. Compila el APK (`assembleDebug` o `assembleRelease`).
2. En GitHub → **Releases** → **Draft a new release**.
3. Etiqueta p. ej. `v1.1`, título y notas (puedes reutilizar [Qué hay de nuevo](#qué-hay-de-nuevo-en-11)).
4. Adjunta `miptvga.apk` y publica.

Los enlaces de descarga del README apuntan a `/releases` y `/releases/latest`.

## Uso rápido

1. Abre la app en el televisor, caja o móvil.
2. Carga una lista M3U por **URL** o **archivo** (explorador interno o picker).
3. Elige grupo y canal; usa **Buscar**, **Guía** o **Favoritos** si lo necesitas.
4. En **Ajustes**: backend (VLC/ExoPlayer), EPG, keep-alive Xtream, compatibilidad de vídeo.
5. **Fullscreen** para ver a pantalla completa.

## Recursos visuales

- Logo: `app/src/main/res/drawable/miptvga.png`
- Banner TV: `app/src/main/res/drawable/tv_banner.*`
- Launcher: `mipmap` / adaptive icons

## Privacidad del repositorio

No subas:

- `local.properties`
- `keystore.properties` / `*.jks` / `*.keystore` / `keystore/`
- carpetas `build/` o `.gradle/`

Los APK van en **Releases**, no en el árbol del código (están ignorados con `*.apk`).

## Notas

- La app declara launcher normal y **LEANBACK_LAUNCHER** para Android TV.
- Sin firma release configurada, `assembleRelease` puede generar un APK no firmado o firmado según tu entorno; para distribución usa firma propia o el APK de la Release publicada.
- Uso destinado a listas IPTV legales a las que tengas acceso legítimo.
