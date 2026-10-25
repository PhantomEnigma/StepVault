# StepVault Technical Plan

## 1. Purpose

StepVault is a clean-room Kotlin Android app that reproduces the step-tracking experience of Xiaomi App Vault:

- Reads daily step records from the MIUI/HyperOS step provider.
- Caches them locally for fast dashboard and widget rendering.
- Shows a circular step goal, distance, calories, active time, and a 7-day chart.
- Provides a 4x2 and a 2x1 home-screen widget shaped after App Vault's health widgets.

It is an independent project. It does not contain App Vault code, resources, or signing material.

## 2. What we learned from App Vault 13.33.48

| Area | Finding |
| --- | --- |
| Package | `com.mi.globalminusscreen` |
| Data source | `content://0@com.miui.providers.steps/item` (Xiaomi multiuser form of `content://com.miui.providers.steps/item`) |
| Projection | `_begin_time, _end_time, _mode, _steps` |
| Mode mapping | provider `1 -> rest (0)`, `2 -> walking (1)`, `3 -> running (2)` |
| Distance fallback | `steps * 60 cm` = `steps * 0.6 m` |
| Calories | speed `< 5 km/h -> 0.04 kcal/step`, `5..10 -> 0.06`, `> 10 -> 0.1` |
| Default goal | `8000` steps |
| Local storage | Room table `step_detail`, daily aggregation with SQL `SUM(...) GROUP BY julianDay` |
| Widgets | Native `RemoteViews`, not MAML. 4x2 initial layout `pa_app_widget_health`, 2x1 `pa_app_widget_health_2x1` |
| Widget states | Unauthorized: header + setup button. Authorized: step card with current steps, goal, progress, details/set-goal actions |
| Feature gate | `miui.util.FeatureParser.getBoolean("support_steps_provider", false)` and MIUI check |

The app deliberately uses the public content-provider route only. It does not use root, Xposed, or App Vault's exported cache provider.

## 3. Architecture

```text
MIUI step provider
        │  ContentResolver query (begin/end/mode/steps)
        ▼
StepProviderClient ── permission/availability detection
        │
        ▼
StepAggregator (pure Kotlin: group by local day, distance/calories/duration)
        │
        ▼
StepRepository ──────── Room cache (`daily_steps`)
        │                DataStore (`goal_steps`)
        │
        ├──────────────► ViewModel / StateFlow ──► Compose dashboard
        │
        └──────────────► WidgetUpdater ──► RemoteViews 4x2 + 2x1

WorkManager (15 min) ──► refresh provider → cache → widgets
```

### Design rules

1. **Single source of truth for UI:** `StepRepository` returns `StepSnapshot`; UI never queries the provider directly.
2. **The widget must work from cache:** if the provider query fails, the last cached values remain visible with a stale/error hint.
3. **Pure aggregation:** `StepAggregator` has no Android dependencies and is unit-testable.
4. **No network:** the app does not request `INTERNET`.
5. **No background foreground service:** WorkManager and the widget's system update cycle are enough for a simple app.

## 4. Technology stack

| Layer | Choice | Version | Reason |
| --- | --- | --- | --- |
| Language | Kotlin | 2.1.x | User requirement; coroutines, concise models |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 | Fast custom ring/chart UI, no XML coupling |
| AppCompat | Not used | - | `ComponentActivity` + Compose needs no AppCompat |
| Widgets | `AppWidgetProvider` + `RemoteViews` | platform | App Vault health widgets are native RemoteViews |
| Local DB | Room | 2.7.2 | Mirrors App Vault's cached step table |
| Preferences | DataStore Preferences | 1.1.1 | Step goal, no XML SharedPreferences |
| Background | WorkManager | 2.10.0 | Periodic sync, survives reboot |
| Async | Kotlin coroutines + Flow | 1.9.x | Repository/ViewModel/widget async work |
| Build | AGP 8.7.3, Gradle 8.9, JDK 17 | - | compileSdk/targetSdk 35 |
| Tests | JUnit 4 + coroutines-test | - | Aggregator and repository-free logic |

Minimum SDK: **30** (same floor as App Vault; `java.time` is available without desugaring).
Compile/target SDK: **35**.

## 5. Provider contract

### Availability

```kotlin
val providerExists = context.packageManager
    .resolveContentProvider("com.miui.providers.steps", 0) != null
```

The manifest includes a `<queries><provider>` entry because Android 11+ package visibility filters provider resolution.

### Query

```kotlin
val projection = arrayOf("_begin_time", "_end_time", "_mode", "_steps")
val selection = "_begin_time >= ? AND _begin_time < ?"
val sortOrder = "_id ASC"
```

Candidate URIs, tried in order:

1. `content://0@com.miui.providers.steps/item`
2. `content://com.miui.providers.steps/item`

### Failure mapping

| Failure | UI state |
| --- | --- |
| Provider not resolvable | `PROVIDER_UNAVAILABLE` |
| `ACTIVITY_RECOGNITION` not granted | `PERMISSION_REQUIRED` |
| `SecurityException` from provider | `PERMISSION_DENIED` |
| Cursor/column/other error | `ERROR` with message |
| Success | `READY` |

### Mode types

`0 = rest`, `1 = walking`, `2 = running`, `-1 = unknown`. The dashboard currently aggregates total steps regardless of mode; the model keeps mode for future breakdowns.

## 6. Aggregation

For every provider row:

```text
durationSec = max(0, (endTime - beginTime) / 1000)
distanceM   = steps * 0.6
speedKmh    = durationSec > 0 ? distanceM / durationSec * 3.6 : 0
kcalPerStep = speedKmh > 10 ? 0.10 : speedKmh >= 5 ? 0.06 : 0.04
calories    = steps * kcalPerStep
```

Rows are grouped by `Instant.ofEpochMilli(beginTime).atZone(zone).toLocalDate()`, matching App Vault's `julianDay` behavior (begin-time day). The last 7 local days are returned, including zero-filled days. Cached daily rows are upserted by `epochDay`, and rows older than 60 days are pruned. A successful query always replaces today's row; cached prior days are preserved when the provider returns no rows for them, so a partial provider range cannot erase history.

## 7. UI specification

Dark surface inspired by App Vault's black widget cards.

- **Header:** "Steps", current date, refresh icon.
- **Access card (conditional):** explains MIUI provider and runtime permission; "Allow access" launches `ACTIVITY_RECOGNITION`. On non-Xiaomi devices, explains that the MIUI provider is unavailable.
- **Hero ring:** 260 dp circular progress, gradient sweep, big formatted step count, subtitle `of 8,000`.
- **Stats:** distance in km, calories in kcal, active minutes.
- **Week chart:** 7 rounded bars, dashed goal line, weekday labels, today highlighted.
- **Bottom actions:** "Set goal" dialog (1,000–100,000), "Refresh now", source/footer text and last sync time.
- **Empty/cached states:** cached data stays visible with a status line when refresh fails.

## 8. Widget specification

### 4x2 (`widget_health_4x2.xml`)

- Rounded black card (`#FF000000`), 8 dp padding, 18 dp corner radius; primary text `#E6FFFFFF`, secondary text `#80FFFFFF`, and an orange step-progress gradient (`#FFFFB233` → `#FFFF6E31`) over a `#4DFF8532` track.
- Setup state: 35 dp walk icon, "Connect steps" title, status subtitle, 42 dp "Set up" button.
- Active state:
  - top row: "Steps", `Set goal`, `Details`
  - large step count
  - `Goal 8,000` and percent complete
  - 6 dp rounded progress bar
  - distance and calories line
- Whole card opens the app; "Set goal" opens the app with the goal dialog; "Details" opens the dashboard.
- When refresh fails but cache exists, the active values stay visible and the percent slot reads `cached`.

### 2x1 (`widget_health_2x1.xml`)

- Compact horizontal card: 36 dp icon, "Steps" + `/ goal` line, auto-sized step count, and a slim progress bar.
- Tap opens the dashboard.
- With no cache and no access, the title becomes "Tap to set up"; with cache, values remain and the goal slot reads `cached`.

Widget metadata: 4x2 `minWidth 314dp`, `minHeight 115dp`, `targetCellWidth 4`, `targetCellHeight 2`; 2x1 `minWidth 115dp`, `minHeight 58dp`, `targetCellWidth 2`, `targetCellHeight 1`. `updatePeriodMillis` is 30 minutes (Android minimum); WorkManager supplies a 15-minute sync while the app is installed.

## 9. Refresh strategy

- Dashboard: refresh on resume, manual refresh, and after permission grant/goal change.
- Widgets: `onUpdate` queries and refreshes asynchronously with `goAsync()`.
- Background: unique `PeriodicWorkRequest` every 15 minutes. After refresh, all widget IDs are updated.
- No ContentObserver service: a process-lifetime observer would require a foreground service, which is out of scope for this simple app. The cache/staleness model keeps behavior predictable.

## 10. Security and privacy

- No `INTERNET` permission.
- No analytics, ads, or third-party SDKs.
- Only `ACTIVITY_RECOGNITION` and Xiaomi's `miui.permission.READ_STEPS` are requested.
- Step data stays in app-private Room/DataStore storage.
- The app reads only columns needed for totals; it does not read location or personal identifiers.
- The exported widget receivers are the only exported components and expose no data.
- Known platform caveat: MIUI's step provider permission model is controlled by Xiaomi and can change across ROM versions.

## 11. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Provider absent on non-Xiaomi devices | Detect with `resolveContentProvider`, show clear unavailable state; document Health Connect as a future adapter |
| `miui.permission.READ_STEPS` silently denied | Catch `SecurityException`, surface `PERMISSION_DENIED`, show setup when no cache exists, otherwise cached values |
| Android 11 package visibility hides provider | `<queries>` provider declaration |
| Widgets cannot use Compose | Custom XML + RemoteViews; progress drawn with platform `ProgressBar` |
| No local Android SDK/JDK in this environment | Source is complete; compile/install steps are documented for Android Studio or CI |
| Provider column names differ across ROMs | Defensive `getColumnIndex` checks and error state |
| Step count resets at midnight | Query by begin-time day; inject `Clock`/`ZoneId` in aggregator tests |

## 12. Build prerequisites

- Android Studio Ladybug or newer, or JDK 17 + Android SDK 35 + Gradle 8.9.
- Xiaomi/Redmi/POCO device running MIUI/HyperOS with the step provider for real data.
- `./gradlew :app:assembleDebug` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

If no Gradle wrapper JAR is present, open the project in Android Studio once to generate it, or run `gradle wrapper --gradle-version 8.9`.
