# Silmaril Multiplatform Migration

Kotlin Multiplatform project: Desktop (JVM + Jewel) + Android. Shares code via commonMain/jvmMain.

## Directory Structure

```
src/
├── commonMain/kotlin/ru/adan/silmaril/
│   ├── misc/ (CoreEnums, RoomEnums)
│   ├── model/ (Creature, Affect)
│   ├── platform/ (Platform, Logger - expect/actual)
│   ├── ui/ (Theme, ConnectionStatus, HealthBar)
│   └── viewmodel/ (Connection, Group, Mobs, Output)
│
├── jvmMain/kotlin/ru/adan/silmaril/
│   ├── mud_messages/ (Jackson XML parsers)
│   ├── network/ (MudConnection, Protocol, AnsiParser)
│   ├── profile/ (Profile, ProfileManager)
│   └── scripting/ (ScriptingEngine, API)
│
├── desktopMain/ (Jewel UI, desktop-specific)
└── androidMain/ (Android app, services)
```

## Key Shared Components

- **Data**: Creature, Affect, enums (AnsiColor, Position, RoomColor)
- **UI**: SilmarilTheme, HealthBar, ConnectionStatusIndicator
- **ViewModels**: ConnectionViewModel, GroupViewModel, MobsViewModel, OutputViewModel
- **Platform**: Logger (expect/actual), Platform info

## jvmMain (Desktop + Android shared)

- **MudConnection**: Socket connection, protocol handling
- **Profile/ProfileManager**: Profile management, connection state
- **Jackson XML parsing**: Room/Group/Lore messages
- **ScriptingEngine**: Triggers, aliases, scripting API

## Build Commands (Windows)

```bash
./gradlew.bat compileKotlinDesktop        # Desktop compile
./gradlew.bat compileDebugKotlinAndroid   # Android compile
./gradlew.bat run                         # Run desktop
./gradlew.bat assembleDebug               # Build APK
```

## Android-Specific Implementations

### Foreground Service (MudConnectionService)
- Keeps connections alive when app backgrounded
- WakeLock for screen-off connections
- Persistent notification (required)
- Survives home/sleep/doze mode

**Integration:**
```
MudConnection.connectionState
  → AndroidProfile observes
  → AndroidProfileManager.connectionStates StateFlow
  → MainActivity updates MudConnectionService
  → Service manages WakeLock + notification
```

### Activity Lifecycle Management

**Key fixes:**
- Call `stopKoin()` in `onDestroy()` + `startKoin {}` in `onCreate()` - ensures fresh state on recreation
- Pending state queue in MainActivity - handles async service binding
- Service doesn't auto-stop when connections reach 0
- Portrait orientation lock in manifest

**Critical:** Activity destruction (long background time) means:
- Koin must be reinitialized completely
- Service binding is async - queue updates until bound
- All singletons get fresh state (coroutines, connections)

### Keyboard Focus
- Uses `WindowInsets.isImeVisible` to detect keyboard state
- Only transfer focus on profile switch if keyboard is open
- Prevents unexpected keyboard popup on closed keyboard + profile switch

## Common Import Changes

From `mud_messages` → `model`/`misc`:
```kotlin
ru.adan.silmaril.model.Creature
ru.adan.silmaril.model.Affect
ru.adan.silmaril.misc.Position
```

## AndroidManifest Critical Settings

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<activity 
    android:screenOrientation="portrait"
    android:configChanges="keyboardHidden|screenSize|smallestScreenSize"
    android:windowSoftInputMode="adjustResize" />

<service
    android:name=".service.MudConnectionService"
    android:foregroundServiceType="dataSync" />
```

---

*Last updated: December 12, 2025 - Activity Lifecycle & Service Fixes*
