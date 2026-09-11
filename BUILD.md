# Build TITAN PULSE

## Requirements

- Node.js 22 LTS or newer compatible Node release
- npm
- Java 21
- Android SDK 36
- Android Build Tools compatible with SDK 36
- Internet access for the first Gradle/Node dependency resolution

## Web validation

```bash
npm install
npm run build:web
```

## Android

```bash
cd android
chmod +x gradlew
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
```

Windows:

```powershell
cd android
.\gradlew.bat assembleRelease
```

The repository includes Gradle wrapper scripts and a pinned Gradle 8.13 distribution URL. The wrapper distribution must be available from the build environment on first use.
