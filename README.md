# miptvga 2.0.0

Aplicación IPTV para Android y Android TV escrita en Kotlin y Jetpack Compose. Permite abrir listas M3U desde una URL o un archivo local, explorar canales y grupos y reproducir contenido con LibVLC o ExoPlayer (Media3).

Incluye navegación mediante mando, búsqueda, favoritos, historial de contenidos vistos, guía EPG/XMLTV y reproducción de directo y VOD. El acceso al contenido depende de la fuente y de los permisos del usuario; el repositorio no incluye listas privadas ni cuentas IPTV.

## Compilar

- JDK 17 o compatible con el Gradle Wrapper incluido.
- Android SDK con plataforma 35 y las herramientas requeridas por Gradle.
- Android Studio o terminal con acceso a los repositorios de dependencias la primera vez.

Configura la ruta del SDK en un `local.properties` propio o mediante el entorno de Android. Este archivo no se publica.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

El APK debug se genera en `app/build/outputs/apk/debug/miptvga.apk`. Los APK y los directorios de compilación no forman parte del código versionado.

La aplicación requiere Android 7.0 (API 24) o posterior. La configuración actual empaqueta las bibliotecas nativas para ARM de 32 y 64 bits (`armeabi-v7a` y `arm64-v8a`).

La versión Android es `2.0.0` (`versionCode = 3`). El identificador de la aplicación se mantiene: `com.toigo.miptvga`.

## Pruebas

Las pruebas JVM están en `app/src/test/`; las pruebas Android generales, en `app/src/androidTest/`.

```sh
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest
```

La última orden requiere un dispositivo o emulador compatible. Las pruebas JVM no certifican reproducción, vídeo, audio ni navegación física en una TV. El comportamiento con un proveedor limitado a una sesión debe comprobarse también con ese proveedor.

## Firma y configuración privada

Para una compilación release firmada, crea un `keystore.properties` local siguiendo `keystore.properties.example` y utiliza tu propio keystore. Ninguno de esos secretos se publica. No incluyas contraseñas, tokens, listas privadas ni URLs con credenciales en código, incidencias o registros compartidos.

El repositorio conserva las configuraciones Gradle, el Wrapper, los recursos Android, el workflow de CI y las pruebas generales de regresión. Los informes provisionales, capturas y material de diagnóstico del desarrollador permanecen fuera del índice mediante exclusiones específicas en `.gitignore`.

## Licencia

El código propio de miptvga se distribuye bajo **GNU General Public License, versión 3 exclusivamente**, identificador SPDX **GPL-3.0-only**. El texto oficial íntegro está en [LICENSE](LICENSE).

Las dependencias y los componentes de terceros conservan sus licencias. Consulta los [avisos y condiciones de distribución](app/src/main/assets/licenses/NOTICE.txt) y los textos legales incluidos en `app/src/main/assets/licenses/`.
