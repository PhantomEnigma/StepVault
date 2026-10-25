# StepVault

A clean-room Kotlin recreation of Xiaomi App Vault's step dashboard and health widgets. Step totals are read from the MIUI/HyperOS step provider, cached locally, and rendered in Jetpack Compose and native `RemoteViews` widgets.

## Features

- Today's steps, goal ring, distance, calories, and active time.
- Interactive seven-day bar chart with a goal line; tap a day to inspect it.
- Hourly step chart for the selected day; tap an hour to see its totals.
- 4x2 and 2x1 home-screen widgets with setup and active states, including distance and calories on both.
- Runtime `ACTIVITY_RECOGNITION` handling plus Xiaomi's `miui.permission.READ_STEPS`.
- Local Room cache for daily and hourly data, including offline/stale rendering.
- 15-minute WorkManager sync, 30-minute system widget refresh, and near-real-time widget updates while the app process is alive.
- No `INTERNET` permission, analytics, ads, or third-party SDKs.

## Data flow

```text
com.miui.providers.steps
  -> StepProviderClient (begin/end/mode/steps)
  -> StepAggregator (day/hour grouping, 0.6 m/step, speed-based kcal)
  -> Room daily_steps + hourly_steps
  -> Compose dashboard + RemoteViews widgets
```

A process-lifetime `ContentObserver` watches the provider and debounces widget refreshes. Android still limits background work to a 15-minute WorkManager minimum and a 30-minute widget update minimum.

The provider query uses `content://0@com.miui.providers.steps/item` and falls back to `content://com.miui.providers.steps/item`, matching App Vault’s user-0 URI first. The app also declares a `<queries>` provider entry for Android 11+ package visibility.

## Requirements

- Xiaomi, Redmi, or POCO device with MIUI/HyperOS for live data.
- Android Studio Ladybug or newer, or JDK 17 + Android SDK 35 + Gradle 8.9.
- minSdk 30, targetSdk 35.

A non-Xiaomi device can build and run the app, but it will show "MIUI step provider not found" because that provider is part of MIUI/HyperOS.

## Build

```bash
cd StepVault
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper (`gradlew`, `gradlew.bat`, wrapper JAR, and properties) is included, but a JDK is still required. If the script is not executable, run `bash gradlew` or `chmod +x gradlew` first. In this workspace the wrapper is present but no JDK/Android SDK is installed, so build verification could not be run.

## Permissions

The app requests:

- `android.permission.ACTIVITY_RECOGNITION` at runtime.
- `miui.permission.READ_STEPS` in the manifest, granted by MIUI/HyperOS.

If the dashboard or widget says access is blocked:

1. Grant Physical activity permission in Android settings.
2. Check MIUI Security > Permissions for StepVault.
3. Tap refresh in the app.

Useful ADB command:

```bash
adb shell pm grant com.stepvault.app android.permission.ACTIVITY_RECOGNITION
```

## Widgets

Long-press the home screen, choose Widgets, and add either:

- **Steps 4x2** - step count, goal, progress, distance, calories, Set goal/Details actions.
- **Steps 2x1** - compact current/goal, progress, distance, and calories with a small top/bottom outer margin.

Widgets show a setup state when there is no cached data and access is unavailable; otherwise they keep the last cached values. They refresh on system updates, resize, provider changes while the app process is alive, every 15 minutes through WorkManager, and after goal changes.

## Project layout

```text
app/src/main/java/com/stepvault/app/
  data/       provider client, repository, DataStore goal, Room cache
  domain/     models and pure aggregation
  ui/         Compose dashboard, ring, chart, cards
  widget/     AppWidgetProviders, RemoteViews renderer, updater
  work/       periodic sync worker
docs/         technical plan, SRS, development plan
```

## Formulas

- Distance fallback: `steps * 0.6 m`.
- Calories: `<5 km/h -> 0.04`, `5-10 km/h -> 0.06`, `>10 km/h -> 0.10` kcal per step.
- Default goal: `8,000` steps.
- Days are grouped by the local date of `_begin_time`; hourly rows use the hour of `_begin_time`.
- A sample that spans two hours is assigned to its starting hour, matching the daily begin-time rule.

These match the fallback behavior found in App Vault 13.33.48.

The MIUI provider exposes only begin/end time, mode, and steps. It does not expose weight, height, gender, foot length, distance, or calories. Height, foot length, and weight could refine estimates through a user-entered profile, but those values would be local assumptions, not provider data, and are not currently collected.

## Limitations

- Live data requires the MIUI/HyperOS step provider; Health Connect is not implemented.
- The widgets are native RemoteViews recreations, not copies of App Vault assets.
- Android clamps widget `updatePeriodMillis` to 30 minutes; WorkManager also has a 15-minute minimum and is subject to Doze.
- Provider change notifications can refresh widgets only while the app process is alive. True out-of-process real-time updates would require a foreground service.
- No root, Xposed, system-app privileges, or App Vault package access.
- The dashboard UI is a dark, clean-room interpretation because App Vault's full health dashboard is not part of its widget resources.
- Distance and calorie estimates use fixed fallbacks because the provider does not expose body or stride data.

## Privacy

Step data stays in app-private Room/DataStore storage. The app has no network permission and does not read contacts, SMS, location, or App Vault's exported cache.

## Disclaimer

StepVault is an independent project and is not affiliated with Xiaomi. "App Vault", "MIUI", and "HyperOS" are trademarks of their respective owners.
