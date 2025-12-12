# Local Build Instructions (Windows)

This document contains instructions for building Silmaril locally on Windows.
For more information on what this program does, YOU MUST read the README.md file.
For more information on what we've been doing in previous sessions, YOU MUST read the MULTIPLATFORM_MIGRATION.md file.

## Prerequisites (already installed on this machine)

- **JBR 21.0.8** (JetBrains Runtime) - Required for desktop builds. 
  - Download from: https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-21.0.8
  - Get the SDK variant (not jcef): `jbrsdk-21.0.8-windows-x64-b895.146.zip`
  - Set `JAVA_HOME` to point to the extracted JBR directory

- **Android SDK** - Required for Android builds
  - Installed via Android Studio or command-line tools
  - AGP version: 8.7.3
  - Target SDK: 35
  - Min SDK: 26

## Working Directory

```
D:\Repos\Silmaril
```

## Build Commands

### Windows Desktop Build

**Compile only:**
```bash
./gradlew.bat compileKotlinDesktop
```

**Create portable distribution:**
```bash
./gradlew.bat createReleaseDistributable
```

**Run desktop application:**
```bash
./gradlew.bat run
```

---

### Android Build

**Compile debug:**
```bash
./gradlew.bat compileDebugKotlinAndroid
```

**Build debug APK:**
```bash
./gradlew.bat assembleDebug
```

**Build release APK:**
```bash
./gradlew.bat assembleRelease
```