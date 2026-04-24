# miptvga
<div align="center">
<img width="781" height="757" alt="miptvga" src="https://github.com/user-attachments/assets/9c21a4fb-6e74-4ac4-9f1e-2cbb22f58f8e" />
</div>

Aplicación IPTV para **Android TV** y cajas Android orientada a listas M3U grandes. Permite cargar listas por URL o desde archivo local, navegar por grupos y canales, marcar favoritos y reproducir emisiones en directo con una interfaz pensada para televisor y mando a distancia.

## Qué hace esta app

- Carga listas **M3U** desde URL.
- Carga listas M3U desde **archivo local**.
- Interfaz adaptada a **Android TV / Leanback launcher**.
- Navegación por **grupos de canales**.
- **Búsqueda** por nombre de canal o grupo.
- **Favoritos** por canal y por grupo.
- Pantalla de **guía EPG/XMLTV**.
- Reproducción con **VLC** o **Media3 ExoPlayer**.
- Modo de compatibilidad de vídeo para equipos problemáticos.
- Soporte para listas muy grandes.

## Stack técnico

- **Kotlin**
- **Jetpack Compose**
- **Android Gradle Plugin 9.1.0**
- **Kotlin 2.2.10**
- **compileSdk 35 / targetSdk 35 / minSdk 24**
- **VLC** (`libvlc-all`)
- **AndroidX Media3 ExoPlayer**

## Requisitos

- **JDK 17** o superior
- Android SDK instalado localmente
- Un dispositivo/emulador con Android TV o Android compatible
- Gradle Wrapper incluido en el proyecto

## Estructura básica del proyecto

```text
miptvga/
├── app/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
└── .gitignore
```

## Configuración local

### 1. SDK de Android

El archivo `local.properties` **no debe subirse al repositorio**. Debe existir solo en tu máquina con una ruta parecida a esta:

```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

Ejemplo típico en Linux:

```properties
sdk.dir=/home/TU_USUARIO/Android/Sdk
```

### 2. Firma release opcional

El proyecto admite firma local para compilar una versión `release` firmada. Para ello utiliza:

- `keystore.properties`
- un archivo de keystore dentro de `keystore/`

Estos archivos **son privados** y están excluidos por `.gitignore`.

Si necesitas preparar tu entorno local, puedes partir del archivo de ejemplo `keystore.properties.example` y adaptarlo con tus propios datos.

## Compilación

### APK debug

```bash
./gradlew assembleDebug
```

APK generada normalmente en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Ejecutar tests unitarios

```bash
./gradlew testDebugUnitTest
```

### Compilar release

```bash
./gradlew assembleRelease
```

### Generar APK release con nombre fijo

```bash
./gradlew packageNamedReleaseApk
```

Salida esperada:

```text
app/build/outputs/apk/release/miptvga-release.apk
```

### Generar bundle

```bash
./gradlew bundleRelease
```

## Uso rápido

1. Abre la app en tu Android TV o caja Android.
2. Carga una lista M3U desde **URL** o desde **archivo local**.
3. Selecciona un grupo y luego un canal.
4. Si lo necesitas, abre **Ajustes** para cambiar backend de reproducción, EPG o compatibilidad de vídeo.
5. Marca canales o grupos como favoritos para acceder más rápido.

## Recursos visuales

- Logo principal: `app/src/main/res/drawable/miptvga.png`
- Banner TV: `app/src/main/res/drawable/tv_banner.*`
- Iconos launcher: recursos `mipmap`

## Privacidad y publicación en GitHub

Antes de subir el proyecto a GitHub, verifica que **no** incluyes:

- `local.properties`
- `keystore.properties`
- archivos `.jks` / `.keystore`
- la carpeta `keystore/`
- carpetas generadas como `build/` o `.gradle/`

Este repositorio ya queda preparado para ello mediante `.gitignore`.

## Notas

- La aplicación está configurada como launcher estándar y también como **LEANBACK_LAUNCHER** para Android TV.
- Si una compilación `release` no tiene firma configurada localmente, el comportamiento dependerá de tu entorno de build y del flujo que quieras usar para firmar después.
