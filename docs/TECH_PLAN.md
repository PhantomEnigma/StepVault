# StepVault Technical Plan

## 1. Purpose

StepVault is a clean-room Kotlin Android app that reproduces the step-tracking experience of Xiaomi App Vault:

- Reads daily step records from the MIUI/HyperOS step provider.
- Caches them locally for fast Today, History, and widget rendering.
- Shows a circular step goal, distance, calories, active time, and a single hourly chart on Today.
- Shows a 60-day History destination with ISO-week summaries, weekly totals, a day list, and an hourly day-detail sheet.
- Provides a minimal 4x2 home-screen widget and the unchanged compact 2x1 widget.

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
StepAggregator (pure Kotlin: group by local day/hour, distance/calories/duration)
        │
        ▼
HistoryAggregator (pure Kotlin: ISO-week grouping, totals, averages, goal days)
        │
        ▼
StepRepository ──────── Room cache (`daily_steps`, `hourly_steps`)
        │                DataStore (`goal_steps`)
        │
        ├──────────────► Today/History ViewModels ──► Compose Today + History
        │
        └──────────────► WidgetUpdater ──► RemoteViews 4x2 + 2x1

ContentObserver (foreground only) ──┐
WorkManager (30 min, widgets only) ─┴─► refresh provider → cache → widgets
```

### Design rules

1. **Single source of truth for UI:** `StepRepository` returns `StepSnapshot`; UI never queries the provider directly.
2. **The widget must work from cache:** if the provider query fails, the last cached values remain visible with a stale/error hint.
3. **Pure aggregation:** `StepAggregator` has no Android dependencies and is unit-testable.
4. **No network:** the app does not request `INTERNET`.
5. **No always-on foreground service:** a 30-minute WorkManager job (only while widgets exist) is the persistent source. A foreground-only `ContentObserver` adds low-latency widget refresh while the app is open.

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

Rows are grouped by `Instant.ofEpochMilli(beginTime).atZone(zone).toLocalDate()`, matching App Vault's `julianDay` behavior (begin-time day). The Today path returns today only; the History path returns a zero-filled 60-day window. Cached daily rows are upserted by `epochDay`, and rows older than 60 days are pruned once per day on a History refresh. Cached days are preserved when the provider returns no rows for them, so a partial provider range cannot erase history.

Hourly buckets are derived from the same samples. A sample is assigned to the hour of its local `beginTime` on its begin-time date; it is not split across hours. Each day receives 24 zero-filled `HourlySteps` entries. Hourly rows are stored in `hourly_steps` with a composite `(epochDay, hour)` key, replaced per refreshed day, and pruned with the same 60-day cutoff. The Today path loads today's hourly rows; History lazily loads hourly rows for one selected day. The repository exposes `refreshToday`/`loadToday`, `refreshHistory`/`loadHistory`, and `loadDayHours`, serializes provider queries with a mutex, uses a 20 s Today TTL and a 5-minute History TTL, and preserves cached days/hours when the provider returns no rows.

ISO-week summaries are computed by `HistoryAggregator`: weeks run Monday through Sunday, the current week ends today, missing window dates are zero-filled, and each week tracks total steps, the average over elapsed days, and the number of days meeting the goal.

The provider exposes no body data. Distance uses `steps * 0.6 m`; calories use the speed bands above. Weight, height, gender, or foot length would require an optional user profile and are not read from MIUI.

## 7. UI specification

Dark surface inspired by App Vault's black widget cards.

- **Navigation:** slim custom bottom bar with equal `Today` and `History` tabs, an accent pill on the selected tab, and a 1 dp top divider. Back from History returns to Today.
- **Today header:** Today title, current date, refresh icon; refresh disabled while loading.
- **Access card (conditional):** explains MIUI provider and runtime permission; "Allow access" launches `ACTIVITY_RECOGNITION`. On non-Xiaomi devices, explains that the MIUI provider is unavailable.
- **Hero ring:** auto-sized circular progress, gradient sweep, animated formatted step count, remaining steps, and a tappable goal line.
- **Stats strip:** compact distance (km), calories (kcal), and active minutes.
- **Today chart:** a single hourly chart with 24 bars for today, tap/keyboard/accessibility selection, and a detail line. There is no week chart or Week/Hourly switch on Today.
- **History summary:** 60-day total, daily average, and best day.
- **Weekly totals:** one interactive bar per ISO week; selecting a week updates the day list. The current week is selected by default.
- **Selected-week list:** date range and weekly total, then day rows newest-first with steps, distance, and a thin goal-progress bar. Tapping a row opens the day sheet.
- **Day-detail sheet:** `ModalBottomSheet` with the full date, steps/distance/calories/active time, goal line, and the same hourly chart. Hourly rows load from cache first and are fetched lazily when missing; cached data remains visible on failure with a retry action.
- **Goal editing:** tapping the hero goal line opens the goal dialog (1,000–100,000) with preset chips and +/- steppers.
- **Footers:** source/status and last sync time on both destinations.
- **Refresh feedback:** pull-to-refresh plus the toolbar action; cached content remains visible during refresh.
- **Empty/cached states:** cached data stays visible with a status line when refresh fails; History shows an explicit empty state before the first sync.

## 8. Widget specification

### 4x2 (`widget_health_4x2.xml`)

- Rounded black card (`#FF000000`), 10 dp padding, 18 dp corner radius; primary text `#E6FFFFFF`, secondary text `#80FFFFFF`, and an orange step-progress gradient (`#FFFFB233` → `#FFFF6E31`) over a `#4DFF8532` track.
- Setup state: 30 dp walk icon, "Connect steps" title, status subtitle; no separate setup button.
- Active state:
  - top row: "Steps" only
  - large step count
  - tappable `Goal 8,000` line and percent complete
  - 5 dp rounded progress bar
  - distance and calories line
- Whole card opens the app; tapping the goal line opens the app with the goal dialog. There are no Details or Set goal buttons.
- When refresh fails but cache exists, the active values stay visible and the percent slot reads `cached`.

### 2x1 (`widget_health_2x1.xml`)

- Outer top/bottom margin is 4 dp only; the card keeps the full available width.
- Inner card padding is 10 dp horizontal and 5 dp vertical; 28 dp icon, "Steps" + `/ goal` row, auto-sized 19 dp step count, 2 dp progress bar, and an 8 dp distance/calories row.
- Tap opens the dashboard; resize triggers a refresh.
- With no cache and no access, the title becomes "Tap to set up"; with cache, values remain and the goal slot reads `cached`.

Widget metadata: 4x2 `minWidth 314dp`, `minHeight 115dp`, `targetCellWidth 4`, `targetCellHeight 2`; 2x1 `minWidth 115dp`, `minHeight 64dp`, `targetCellWidth 2`, `targetCellHeight 1`. `updatePeriodMillis` is `0`; a 30-minute WorkManager job supplies updates only while at least one widget exists.

## 9. Refresh strategy

- Today: `refreshToday` on tab visible (20 s TTL), manual refresh, and after permission grant/goal change.
- History: `loadHistory` for immediate cache, then `refreshHistory` when the tab opens and the 5-minute TTL has expired; manual refresh and pull-to-refresh force a query. Hourly rows load lazily for the selected day.
- Widgets: `onUpdate`, `onAppWidgetOptionsChanged`, and the MIUI update broadcast query and refresh asynchronously with `goAsync()` using `refreshToday`.
- Provider observer: `StepVaultApp` registers a `ContentObserver` on both candidate URIs only while the app is in the foreground and at least one widget exists. Notifications are debounced for 1.5 s and throttled to a 15 s minimum interval before `WidgetUpdater.updateAll()`.
- Background: unique `PeriodicWorkRequest` every 30 minutes, scheduled only while widgets exist and cancelled when the last widget is removed. After refresh, all widget instances are updated.
- Overlapping refreshes are coalesced by the repository mutex/TTL; widget renders are skipped when the snapshot signature is unchanged.
- No foreground service is used, so out-of-process updates still depend on the 30-minute schedule; this is the battery-safe trade-off.

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
| Widget does not update immediately while app is dead | Document platform limits; foreground-only `ContentObserver` improves updates only while the app is open |
| Provider change notifications are noisy | 1.5 s debounce plus 15 s minimum interval before widget refreshes |
| Distance/calories are only estimates | Fixed fallbacks match App Vault; body-profile refinements are documented as a future option |

## 12. Build prerequisites

- Android Studio Ladybug or newer, or JDK 17 + Android SDK 35 + Gradle 8.9.
- Xiaomi/Redmi/POCO device running MIUI/HyperOS with the step provider for real data.
- `./gradlew :app:testDebugUnitTest` then `./gradlew :app:assembleDebug` and install the debug APK, or build `assembleRelease` for the minified/resource-shrunk artifact.

If no Gradle wrapper JAR is present, open the project in Android Studio once to generate it, or run `gradle wrapper --gradle-version 8.9`.
