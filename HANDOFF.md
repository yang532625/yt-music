# LEE ESTO PRIMERO — handoff para el agente de programación

**No crees otro AGENTS.md.**  
**No sigas el AGENTS.md viejo de Metrolist (35 líneas, `universalFoss`, “no editar markdown”).**  
Ese texto de upstream está **obsoleto** para este fork.

Este archivo es el resumen rápido. El briefing completo (errores, apariencia, arquitectura) está en **[AGENTS.md](AGENTS.md)** — léelo antes de programar.

---

## Qué debes hacer ahora

1. Abrir el emulador `emulator-5554` (no Chrome, no el sitio de escritorio).
2. Compilar `assembleFossDebug`, instalar, `force-stop`, `screencap`.
3. Comparar con YouTube Music oficial **en el emulador/teléfono**, no con YouTube Music Desktop de Windows.
4. Arreglar bugs de **reproducción** (Samsung) y **apariencia** (player, Home, mini-player) según las tablas en AGENTS.md.

Implementar + probar en dispositivo. No solo documentar.

---

## Proyecto

| | |
|---|---|
| Carpeta | `C:\Users\Yang\Development\APKs-Development\Android apps\Android\Metrolist-ytm` |
| GitHub | https://github.com/yang532625/yt-music |
| Package | `com.yang.ytmusic` |
| Nombre | **YT Music** |
| Dueño | Yang Torres |

**No es Metrolist stock.** No instales `com.metrolist.music.debug`.

---

## Launcher actual

```
MainActivityAlias  →  com.metrolist.music.MainActivity  (Compose híbrido)
```

**No** es `YtMusicWebActivity`. Ese WebView completo quedó como **legacy/rollback** en el repo.

| Tab | Implementación |
|-----|----------------|
| Home | Compose + InnerTube API |
| Samples | WebView embebido (`SamplesWebViewScreen`) → handoff a `MusicService` |
| Search | Compose |
| Library | Compose |

**4 tabs.** No hay tab Upgrade. Premium es implícito desde el primer uso (sin paywall, sin pantalla "INCLUDED").

Reproducción: **ExoPlayer** vía `MusicService`, no audio del WebView (excepto preview en Samples, que se pausa al handoff).

---

## Compilar (Windows)

JDK 21 de Android Studio:

```
C:\Program Files\Android\Android Studio\jbr
```

```bat
cd /d "C:\Users\Yang\Development\APKs-Development\Android apps\Android\Metrolist-ytm"
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleFossDebug
```

APK **correcto**:

```
app\build\outputs\apk\foss\debug\app-foss-debug.apk
```

El path `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk` es de **upstream Metrolist**. **No lo uses.**

---

## Instalar y abrir

```bat
adb devices
adb -s emulator-5554 install -r "app\build\outputs\apk\foss\debug\app-foss-debug.apk"
adb -s emulator-5554 shell am force-stop com.yang.ytmusic
adb -s emulator-5554 shell am start -n com.yang.ytmusic/com.metrolist.music.MainActivityAlias
adb -s emulator-5554 shell screencap -p /sdcard/Download/ytm.png
adb -s emulator-5554 pull /sdcard/Download/ytm.png %TEMP%\ytm.png
```

Samsung (login/playback real): serial `R5CX40GP43D`, mismo `install` / `am start`. Cuenta: `yangcyb7@gmail.com`.

Siempre `force-stop` después de instalar: `MusicService`, PoToken WebView y Samples deben recargar código nuevo.

YouTube Music oficial (referencia visual):

```bat
adb -s emulator-5554 shell monkey -p com.google.android.apps.youtube.music -c android.intent.category.LAUNCHER 1
```

---

## Prioridades (ver AGENTS.md para detalle)

1. **Apariencia visual completa** — la app todavía se parece más a Metrolist open-source que a YTM oficial. Comparar screenshots y arreglar TODO (Home feed, chips, cards, spacing, player, etc.)
2. **Playback Samsung:** error "Sign in to confirm you're not a bot", `IO_UNSPECIFIED` → `LoginScreen`, `App.kt`, `PoTokenWebView.kt`, `PlaybackError.kt`.
3. **Now-playing:** estilo YTM oficial (círculo blanco, like/dislike) — no botón rojo pill Metrolist → `Player.kt`.
4. **Updater GitHub:** verificar que la app detecta releases nuevas. Push un release y probar. Arreglar si no funciona.
5. Fix error de compilación de tests (`dp` en `YtmAppearanceTest.kt`)
6. **Samples handoff:** tap en canción → `MusicService` sin audio doble.

---

## Prohibido

- Extraer fuentes/iconos del APK oficial `com.google.android.apps.youtube.music`.
- Reinstalar `com.metrolist.music.debug`.
- Tab Upgrade o upsell Premium.
- Tocar FreeInternet, iOS u otras apps de la carpeta `Android`.
- Subir versión, commit o push si Yang no lo pide.
- Volver al launcher WebView completo o al player-page de escritorio salvo que Yang lo pida.

---

## Más detalle

[AGENTS.md](AGENTS.md) — arquitectura, catálogo de errores, checklist de apariencia, archivos clave.

Workspace Cursor:

```
C:\Users\Yang\Development\APKs-Development\Android apps\Android
```
