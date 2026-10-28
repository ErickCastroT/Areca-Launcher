# Areca Launcher

🇬🇧 **English** · 🇲🇽 [Español](README.es.md)

An Android launcher written in Kotlin with Jetpack Compose. It shows your favorite apps as bubbles in a honeycomb
that scrolls vertically with a magnifying effect toward the center, or in straight rows.

> Status: in development. Tested on a Samsung phone running Android 13; it still needs testing on more devices and
> Android versions.

## Inspiration

The main inspiration is the Palm launcher, which showed up on a miniature phone in 2018. A few alternatives to that
design existed over the years, but none of them is available today. Areca is my attempt to bring that peculiar concept
back to life.

## Screenshots

| Home screen | App list | Settings |
|:---:|:---:|:---:|
| <img src="docs/screenshots/home.jpg" width="220" alt="Home screen"> | <img src="docs/screenshots/apps.jpg" width="220" alt="App list"> | <img src="docs/screenshots/settings.jpg" width="220" alt="Settings"> |

| Landscape home | Landscape app list |
|:---:|:---:|
| <img src="docs/screenshots/home-landscape.jpg" width="360" alt="Landscape home"> | <img src="docs/screenshots/apps-landscape.jpg" width="360" alt="Landscape app list"> |

### In motion

| Home: fisheye and shortcuts | App list: letters and fast scroll | Look: theme, accent and icons | Backgrounds |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/demo-home.gif" width="200" alt="Home screen in motion"> | <img src="docs/screenshots/demo-drawer.gif" width="200" alt="App list in motion"> | <img src="docs/screenshots/demo-settings.gif" width="200" alt="Look settings in motion"> | <img src="docs/screenshots/demo-backgrounds.gif" width="200" alt="Backgrounds"> |

## What it does

- **Bubble home screen** in a honeycomb (default) or in rows; configurable columns, size, spacing, padding and
  framing, with five icon shapes. Optionally the bubbles drift as you tilt the phone (accelerometer, no permission needed).
- **Favorites, folders and hidden apps**: reorder by dragging, group into folders, hide apps.
- **Icons**: installed icon packs (ADW/Nova, applying the pack's background and mask to apps it does not cover), Android 13 monochrome themed icons, a different icon per app or from the gallery, and per-app framing.
- **Widgets** right on the home screen, above or below the bubbles; in landscape they sit in their own column.
- **App list** with search and recents, and in portrait split by letter with a fast-scroll index.
- **Gestures** (optional): double tap to lock, swipe down for notifications, and swipe up from the bottom for recent
  apps. They use an accessibility service that only performs those actions.
- **Notification badges** (optional): they only count how many notifications each app has; they never read the
  content.
- **Theme**: system (Material You, light or dark), dark, or pure black (AMOLED), with a selectable accent color.
- **Background**: the system one, an image from your gallery, solid colors or gradients, with adjustable dimming.
- **Backup** of your settings to a file, and Spanish and English interface.

Nothing leaves the phone: the app does not use the internet and does not send any data.

## Build

Requirements: a recent Android Studio (or JDK 17+ and the Android SDK with API 35) and a device or emulator running
Android 8.0 (API 26) or later.

```bash
./gradlew assembleDebug      # debug APK
./gradlew installRelease     # installs the release build on the connected device
```

Notes:

- `local.properties` (SDK path) is created by Android Studio and is not committed.
- The `release` variant is signed with the **debug** key, only for performance testing. To publish, set up your own
  signing key.
- When you build or install, the counter in [`version.properties`](version.properties) goes up by itself. Versions
  follow the `YY.MM.N` format (year, month and build number within the month); the codename lives in
  [`app/build.gradle.kts`](app/build.gradle.kts).

## Structure

```
app/src/main/java/com/areca/launcher/
  data/   settings (DataStore), apps, icons, widgets, backup, folders
  ui/     screens (home, app list, settings), themes and Compose components
```

## Technology

Kotlin 2.0, Jetpack Compose (Material 3), DataStore, AppWidgetHost and LauncherApps. Gradle 8.11 with Android Gradle
Plugin 8.9.

## License

Areca Launcher is source-available under the [PolyForm Strict License 1.0.0](LICENSE): you may read the code and use
the app for noncommercial purposes, but you may not distribute it or make changes or new works based on it.

## Third-party licenses

See [`LICENSES.md`](LICENSES.md).

## Author

Erick Castro, 2026.
