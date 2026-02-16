# Mini Notes

A minimal notes app optimized for e-ink displays (600×800), such as the Minimal Phone. It also comes with a desktop client.

Mostly created by cursor composer model

## Features

- **E-ink optimized**: Monochrome UI, no animations, high contrast
- **Simple**: Create, edit, and delete notes (no title—just type)
- **Markdown files**: Notes saved as `.md` files in app storage
- **WebDAV sync**: Optional cloud sync (Nextcloud, etc.) via Settings

## Platforms

### Android

```bash
cd android
# Ensure ANDROID_HOME is set (or create local.properties with sdk.dir)
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

#### Release build

1. Create a keystore (one-time, from `android/`):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias mininotes -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in your keystore details.

3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

Output: `android/app/build/outputs/apk/release/app-release.apk`

#### Install on device

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
# or for release:
adb install android/app/build/outputs/apk/release/app-release.apk
```

### Electron (Desktop)

```bash
cd electron
npm install
npm start
```

To build distributable packages:

```bash
cd electron
npm run build
```

Output: `electron/dist/`

## GitHub Release

1. Create a new repository on GitHub and push your code.

2. Create a release:
   - Go to **Releases** → **Create a new release**
   - Tag: `v1.0` (or your version)
   - Title: e.g. `v1.0`

3. Build the release APK locally, then upload `app-release.apk` as an asset.

4. For automated releases, add a GitHub Action (e.g. `.github/workflows/release.yml`) that builds on tag push. Note: signing in CI requires storing the keystore as a GitHub secret.

## WebDAV Sync

1. Open **Settings** (gear icon on the notes list)
2. Enter your WebDAV folder URL (e.g. `https://cloud.example.com/remote.php/dav/files/user/Notes/`)
3. Enter username and password (HTTP Basic auth)
4. Tap **Save**, then **Sync Now**

Sync runs automatically on app start and when you tap Sync. Notes auto-upload 5 seconds after you stop typing. (Android and Electron)

## Design

- Black & white only
- Monochrome vector icons
- No animations or transitions
- Layout tuned for 600×800 e-ink displays
