# StepVault Development Plan

## 1. Delivery strategy

Build the app in vertical slices so that each phase produces something checkable:

1. Project skeleton and theme.
2. Provider + aggregation + cache (the data spine).
3. Dashboard.
4. Widgets + background refresh.
5. Tests, docs, and release checks.

No phase depends on App Vault being installed. The only external dependency is the MIUI step provider on the target device.

## 2. Work breakdown

### Phase 0 — Discovery (complete)

- Decompile App Vault 13.33.48.
- Identify health widget layouts, colors, dimensions, provider contract, formulas, and goal default.
- Confirm that health widgets are native RemoteViews, not MAML.
- Record findings in `TECH_PLAN.md`.

### Phase 1 — Skeleton

- Create Gradle project, version catalog-free build files, and include the Gradle wrapper.
- Add manifest, theme, launcher icon, strings, colors.
- Add Compose BOM, Material 3, lifecycle, Room, DataStore, WorkManager, KSP.
- Add `StepVaultApp` + `AppContainer`.
- Verify: project syncs; empty `MainActivity` launches.

### Phase 2 — Data spine

- `StepSample`, `DaySteps`, `StepSnapshot`, `StepStatus` models.
- `StepAggregator` with distance/calorie/duration logic and zero-filled 7-day series.
- `StepProviderClient` with availability, permission, dual URI, defensive cursor parsing, failure mapping.
- Room entity/DAO/database for `daily_steps`.
- `GoalStore` using DataStore.
- `StepRepository` with `refresh()` and `loadSnapshot()`.
- Unit tests for aggregation.
- Verify: aggregator tests pass; repository returns `PROVIDER_UNAVAILABLE` on non-Xiaomi.

### Phase 3 — Dashboard

- `StepViewModel` + factory + `StepUiState`.
- `StepScreen` with access card, ring, stats, week chart, goal dialog, refresh.
- Runtime permission launcher; permission re-check on resume.
- Goal dialog intent extra handling from the widget.
- Verify: fake/real data renders; permission states behave.

### Phase 4 — Widgets

- 4x2 and 2x1 XML layouts and drawables.
- `WidgetRenderer` with setup/active states, percent, distance/calories.
- `HealthWidgetProvider` and `HealthWidget2x1Provider` using `goAsync()`.
- Click intents (root/details/setup/set-goal).
- `WidgetUpdater`.
- Verify: widgets appear in picker; both states render; taps open the right screen.

### Phase 5 — Background refresh

- `StepSyncWorker` with unique periodic work, 15-minute interval.
- Schedule from `Application`.
- Refresh widgets after sync.
- Verify: run work manually; widgets update; failure preserves cache.

### Phase 6 — Hardening

- String externalization and content descriptions.
- Edge cases: midnight, zero steps, huge goal, provider denial, malformed rows.
- README with build/install/permission instructions.
- Check no INTERNET permission, no analytics, no App Vault imports.
- Run unit tests and lint if a toolchain is available.

## 3. File layout

```text
StepVault/
├── app/src/main/java/com/stepvault/app/
│   ├── StepVaultApp.kt              Application + container + work scheduling
│   ├── MainActivity.kt              Compose host, goal-dialog intent extra
│   ├── data/
│   │   ├── GoalStore.kt             DataStore goal
│   │   ├── StepProviderClient.kt    MIUI provider query + status
│   │   ├── StepRepository.kt        cache + provider orchestration
│   │   └── local/                   Room entity, DAO, database
│   ├── domain/
│   │   ├── Models.kt                StepSample, DaySteps, StepSnapshot, StepStatus
│   │   └── StepAggregator.kt        pure formulas/grouping
│   ├── ui/
│   │   ├── StepScreen.kt            dashboard composable
│   │   ├── StepViewModel.kt         StateFlow + actions
│   │   ├── components/              ring, chart, cards, permission card
│   │   └── theme/                   colors, typography, theme
│   ├── widget/
│   │   ├── HealthWidgetProvider.kt  4x2 receiver
│   │   ├── HealthWidget2x1Provider.kt
│   │   ├── WidgetRenderer.kt        RemoteViews rendering
│   │   └── WidgetUpdater.kt         suspend update-all
│   ├── work/StepSyncWorker.kt
│   └── util/                        formatters, dates
├── app/src/main/res/                layouts, drawables, xml info, values
├── app/src/test/                    aggregator tests
└── docs/                            TECH_PLAN, SRS, DEV_PLAN
```

## 4. Detailed task list

| ID | Task | Outcome |
| --- | --- | --- |
| T1 | Gradle/manifest/theme skeleton | App builds (with SDK) |
| T2 | Domain models + aggregator | Pure, tested formulas |
| T3 | Provider client | Queries both URIs, maps failures |
| T4 | Room cache + DAO | 60-day cache, 7-day reads |
| T5 | GoalStore | Persistent goal |
| T6 | Repository | Refresh/upsert/snapshot |
| T7 | ViewModel | Loading/data/error states |
| T8 | Compose ring + stats | Dashboard hero |
| T9 | Week chart | 7 bars + goal line |
| T10 | Permission/access card | Grant + retry flow |
| T11 | Goal dialog | Validation + persistence |
| T12 | 4x2 widget layout | Setup + active RemoteViews |
| T13 | 2x1 widget layout | Compact RemoteViews |
| T14 | Widget rendering/providers | Update + click intents |
| T15 | Worker scheduling | 15-minute refresh |
| T16 | Tests/docs | Unit tests + README |
| T17 | Static verification | No INTERNET/App Vault refs; imports compile logically |

## 5. Test plan

### Unit tests (JVM)

- `aggregate_groupsByLocalDay`
- `aggregate_zeroFillsMissingDays`
- `distance_usesPointSixMetersPerStep`
- `calories_useSpeedBands` (slow, moderate, fast, zero-duration)
- `duration_clampsNegativeToZero`
- `progress_clampsAt100`
- `formatting` (steps grouping, km, kcal) if formatters are pure

### Manual tests (device)

1. Fresh install on Xiaomi: setup card → grant → steps appear.
2. Deny activity permission: setup card remains; widget setup state.
3. Revoke permission in Settings: app returns to setup after resume/refresh.
4. Non-Xiaomi emulator: provider-unavailable card, no crash.
5. Goal change: dashboard ring/widgets/percent update.
6. Add 4x2 and 2x1 widgets; test each click target.
7. Wait for/force WorkManager: widgets refresh.
8. Cross midnight: today resets; previous day remains in chart.
9. Airplane mode: no behavioral change (no network dependency).
10. Reboot: widgets still render cached/last synced data.

### Static checks

- `grep` manifest for `INTERNET` → none.
- `grep` sources for `com.mi.globalminusscreen` → none.
- `grep` sources for analytics/ad SDKs → none.
- Confirm only expected exported receivers/activity.

## 6. Build and run

Prerequisites: JDK 17, Android SDK 35, Gradle 8.9 (or Android Studio Ladybug+).

```bash
cd StepVault
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The wrapper JAR is included. If local execution is blocked by filesystem permissions, run `bash gradlew` or copy the project to an executable location.

Widget testing:

```bash
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE \
  -n com.stepvault.app/.widget.HealthWidgetProvider
adb shell dumpsys appwidget | grep -A5 stepvault
```

## 7. Release checklist

- [ ] Version name/code set.
- [ ] Launcher icon and label correct.
- [ ] Both widgets declared with metadata.
- [ ] `<queries>` provider entry present.
- [ ] No INTERNET/analytics/App Vault code.
- [ ] Unit tests pass.
- [ ] Lint has no blocking errors.
- [ ] README build/permission notes updated.
- [ ] Tested on at least one Xiaomi device and one non-Xiaomi device/emulator.

## 8. Risks to schedule

- Xiaomi ROM differences may change provider behavior; allow extra manual test time.
- Widget refresh cadence is constrained by Android (30 min system minimum); WorkManager's 15 min is also a minimum and subject to Doze.
- No local Android toolchain in the current environment means compilation is deferred to Android Studio/CI.
