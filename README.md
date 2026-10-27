# Areca Launcher

🇬🇧 **English** · 🇲🇽 [Español](README.es.md)

An Android launcher written in Kotlin with Jetpack Compose. It shows your favorite apps as bubbles in a honeycomb
that scrolls vertically with a magnifying effect toward the center, or in straight rows.

> Status: in development. Tested on a Samsung phone running Android 13; it still needs testing on more devices and
> Android versions.

## What it does

- **Bubble home screen** in a honeycomb (default) or in rows; configurable columns, size, spacing, padding and
  framing, with five icon shapes.
- **Favorites, folders and hidden apps**: reorder by dragging, group into folders, hide apps.
- **Icons**: installed icon packs (ADW/Nova), a different icon per app or from the gallery, and per-app framing.
- **Widgets** right on the home screen, above or below the bubbles; in landscape they sit in their own column.
- **App list** with search and recents, and in portrait split by letter with a fast-scroll index.
- **Gestures** (optional): double tap to lock, swipe down for notifications, and swipe up from the bottom for recent
  apps. They use an accessibility service that only performs those actions.
- **Notification badges** (optional): they only count how many notifications each app has; they never read the
  content.
- **Theme**: Android dynamic colors (Material You) or a custom palette; light and dark.
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

## Third-party licenses

See [`LICENSES.md`](LICENSES.md).

## Author

Erick Castro, 2026.
