# StepVault Software Requirements Specification

## 1. Introduction

### 1.1 Purpose

This document defines the functional and non-functional requirements for StepVault, a Kotlin Android app that mirrors Xiaomi App Vault's step-tracking dashboard and home-screen widgets while reading data from the MIUI/HyperOS step provider.

### 1.2 Scope

In scope:

- Reading step records from `com.miui.providers.steps`.
- Runtime permission handling for `ACTIVITY_RECOGNITION` and Xiaomi's `miui.permission.READ_STEPS`.
- Daily aggregation of steps, distance, calories, and active time.
- Local caching of the last 60 days.
- A Compose dashboard with goal ring, stats, and a 7-day chart.
- A configurable daily step goal (default 8,000).
- 4x2 and 2x1 widgets with setup and active states.
- Periodic background refresh.

Out of scope:

- Root, Xposed, or system-app installation.
- Reading App Vault's exported cache provider.
- Health Connect integration (documented as a future adapter).
- Cloud sync, accounts, social features, ads, or analytics.
- Workout tracking, GPS, heart rate, sleep, or other health data.
- Pixel-perfect copying of App Vault assets; the UI is a clean-room recreation.

### 1.3 Definitions

| Term | Meaning |
| --- | --- |
| MIUI provider | Xiaomi system content provider exposing aggregated step records |
| Sample | One provider row: begin time, end time, mode, steps |
| Day record | Aggregated totals for one local calendar day |
| Goal | User-configured daily step target |
| Snapshot | Dashboard/widget input: today, 7 days, goal, status, last sync |

## 2. Stakeholders and actors

- **Device user:** grants permission, views steps, sets goal, adds widget.
- **MIUI step provider:** supplies step samples; requires Xiaomi ROM and permission.
- **Android platform:** app widget host, WorkManager scheduler, permission dialogs.
- **Developer/maintainer:** builds and installs the app.

## 3. Assumptions and dependencies

1. The device is a Xiaomi/Redmi/POCO device with MIUI/HyperOS and `support_steps_provider`.
2. `ACTIVITY_RECOGNITION` is grantable and `miui.permission.READ_STEPS` is granted by the ROM or security center.
3. The provider exposes `_begin_time`, `_end_time`, `_mode`, `_steps`.
4. System time zone is the user's intended step day boundary.
5. The app has no network dependency.

## 4. Functional requirements

### FR-1 Access detection and permissions

- FR-1.1 The app shall detect the MIUI provider using `PackageManager.resolveContentProvider`.
- FR-1.2 The app shall declare and request `android.permission.ACTIVITY_RECOGNITION` at runtime.
- FR-1.3 The app shall declare `miui.permission.READ_STEPS` in the manifest.
- FR-1.4 If the provider is missing, the app shall show "MIUI step provider not found" and shall not crash.
- FR-1.5 If the activity permission is missing, the app shall show an explanation and an "Allow access" action.
- FR-1.6 If the provider throws `SecurityException`, the app shall show a denial state and retain cached data.
- FR-1.7 Granting permission shall trigger a refresh without requiring an app restart.

### FR-2 Step synchronization

- FR-2.1 The app shall query the provider for records from the start of the local day 6 days ago through now.
- FR-2.2 The query projection shall be limited to `_begin_time`, `_end_time`, `_mode`, `_steps`.
- FR-2.3 The app shall try `content://0@com.miui.providers.steps/item` first, then `content://com.miui.providers.steps/item`.
- FR-2.4 The app shall treat `_mode` as optional and skip malformed rows; missing required columns shall produce an error state.
- FR-2.5 Successful samples shall be aggregated and upserted into the Room cache.
- FR-2.6 Cache rows older than 60 days shall be pruned.
- FR-2.7 Synchronization shall be idempotent.

### FR-3 Aggregation

- FR-3.1 Distance shall be `steps * 0.6 m` when only steps are available.
- FR-3.2 Calories shall use speed-based factors: `<5 km/h: 0.04`, `5–10 km/h: 0.06`, `>10 km/h: 0.1` kcal per step.
- FR-3.3 Active time shall be the sum of `(endTime - beginTime)` in seconds, clamped at zero per sample.
- FR-3.4 Samples shall be grouped by the local date of `_begin_time`.
- FR-3.5 The 7-day series shall include zero-filled days and be ordered oldest to newest.
- FR-3.6 Today's totals shall use the current local date.

### FR-4 Dashboard

- FR-4.1 The app shall show today's step count and goal.
- FR-4.2 The app shall show a circular progress ring, clamped visually to 100%.
- FR-4.3 The app shall show distance (km), calories (kcal), and active time.
- FR-4.4 The app shall show a 7-day bar chart with a goal reference line.
- FR-4.5 The app shall show a status/footer line with source and last refresh time.
- FR-4.6 The app shall provide a manual refresh action.
- FR-4.7 The app shall show a loading indicator during initial sync.
- FR-4.8 The app shall keep the last successful data visible when a later sync fails.

### FR-5 Goal management

- FR-5.1 The default goal shall be 8,000 steps.
- FR-5.2 The user shall be able to set a goal between 1,000 and 100,000 inclusive.
- FR-5.3 Invalid/non-numeric input shall be rejected without changing the goal.
- FR-5.4 The goal shall persist across restarts and be used by widgets.
- FR-5.5 The goal shall be editable from the dashboard and from the 4x2 widget action.

### FR-6 Widgets

- FR-6.1 The app shall provide a 4x2 widget.
- FR-6.2 The app shall provide a 2x1 widget.
- FR-6.3 Widgets shall show a setup state when data access is unavailable and no cached data exists; otherwise cached values remain visible.
- FR-6.4 Widgets shall show steps, goal, and a progress bar when data is available; the 4x2 widget also shows percent complete.
- FR-6.5 The 4x2 widget shall show distance and calories.
- FR-6.6 Tapping a widget shall open the dashboard.
- FR-6.7 The 4x2 "Set goal" action shall open the dashboard with the goal dialog visible.
- FR-6.8 The 4x2 "Details" action shall open the dashboard.
- FR-6.9 Widgets shall refresh on `APPWIDGET_UPDATE`, after background sync, and after goal changes.
- FR-6.10 Widgets shall render within RemoteViews restrictions without Compose.

### FR-7 Background refresh

- FR-7.1 The app shall enqueue a unique periodic WorkManager job every 15 minutes.
- FR-7.2 The job shall refresh the provider data and update all widget instances.
- FR-7.3 The job shall not run if a previous instance is still active.
- FR-7.4 Failure shall not crash the app or erase the cache.

### FR-8 Error handling and privacy

- FR-8.1 Errors shall be represented by a status enum and optional message.
- FR-8.2 The app shall not declare `INTERNET`.
- FR-8.3 The app shall not include analytics, ads, or third-party tracking.
- FR-8.4 Step data shall remain in app-private storage.
- FR-8.5 The widget receivers shall not expose data beyond reading their own `RemoteViews`.

## 5. Non-functional requirements

- **NFR-1 Performance:** cached dashboard render under 500 ms on a mid-range device; provider query off the main thread.
- **NFR-2 Reliability:** no crash when provider columns, permission, or widgets are absent.
- **NFR-3 Compatibility:** minSdk 30, targetSdk 35; Xiaomi and non-Xiaomi devices (graceful degradation).
- **NFR-4 Maintainability:** pure aggregation logic, single repository, no duplicate formulas.
- **NFR-5 Testability:** aggregator unit tests without Android runtime.
- **NFR-6 Accessibility:** content descriptions for icons, 48 dp touch targets for primary app controls, readable contrast, and full-card widget taps.
- **NFR-7 Privacy:** no network permission; no identifying data collected.
- **NFR-8 Localization:** English strings externalized in `strings.xml`; widget text uses resources where practical.

## 6. Acceptance criteria

1. On a Xiaomi device with the step provider, after granting permission, the dashboard shows today's steps matching the system step counter within one refresh.
2. Distance approximately equals `steps * 0.6 / 1000` km when no provider distance is used.
3. Setting the goal to 10,000 updates the ring, remaining steps, and both widgets.
4. Removing the activity permission shows the access state; widgets keep cached values or show setup when no cache exists, without a crash.
5. On a non-Xiaomi emulator, the app shows the provider-unavailable state and does not crash.
6. Adding either widget from the launcher renders the correct layout and opens the app on tap.
7. Reboot/next-day behavior: data remains cached and refreshes when the worker or app runs.
8. `./gradlew :app:testDebugUnitTest` passes the aggregator tests.
9. No `INTERNET` permission appears in the built manifest.
10. The app does not read from App Vault's package or provider.

## 7. Future requirements (not implemented)

- Health Connect adapter for non-Xiaomi devices.
- Distance from provider columns when available.
- Per-mode breakdown (rest/walking/running/cycling).
- Historical range selection beyond 7 days.
- Locale-aware distance units (mi/ft).
- ContentObserver-based near-real-time widget updates via a user-enabled foreground service.
