# StepVault Todo

## Discovery

- [x] Decompile and inspect App Vault 13.33.48.
- [x] Identify MIUI step provider URI, projection, and mode mapping.
- [x] Recover distance/calorie formulas and default goal.
- [x] Reconstruct 4x2 and 2x1 health widget layouts and states.
- [x] Confirm widgets are native RemoteViews (not MAML).

## Planning

- [x] Technical plan (`docs/TECH_PLAN.md`).
- [x] Software requirements specification (`docs/SRS.md`).
- [x] Development plan (`docs/DEV_PLAN.md`).

## Implementation

- [x] Gradle project, manifest, permissions, theme, launcher icon.
- [x] Domain models and pure step aggregator.
- [x] MIUI provider client with dual URI and failure mapping.
- [x] Room daily cache and DataStore goal.
- [x] Repository refresh/load snapshot.
- [x] Compose dashboard: ring, compact metrics, one `Week | Hourly` chart switch, pull-to-refresh, access card, goal dialog.
- [x] Minimal 4x2 widget with setup/active states and tappable goal line; 2x1 widget preserved with polish.
- [x] 30-minute widgets-only WorkManager sync and foreground-only ContentObserver widget refresh.
- [x] R8/resource shrinking, English-only resources, extended icon pack removed.
- [x] Hourly aggregation, Room cache, and selected-day UI.
- [x] Unit tests for aggregation, hourly buckets, formulas, and progress.
- [x] README/docs updated for single-chart UI, hourly data, widget changes, refresh behavior, and provider/profile limits.

## Refinement pass

- [x] Add repository refresh modes, request coalescing, and cache preservation for empty/partial provider results.
- [x] Fix progress percent truncation and add boundary tests.
- [x] Gate WorkManager/observer work on widget existence and foreground lifecycle.
- [x] Remove duplicate widget refresh pipelines and skip unchanged renders.
- [x] Add chart custom actions and empty states.
- [x] Add release size/lint CI reporting.

## History tab and Today redesign

- [x] Confirm scope: 60-day window, slim bottom tabs, day-detail bottom sheet, weekly totals bars, on-open refresh with 5-minute TTL.
- [x] Write `docs/HISTORY_PLAN.md`.
- [x] Add `WeekSummary`, `HistorySnapshot`, `DayHours`, and `HistoryAggregator` with unit tests.
- [x] Split repository into `refreshToday` / `loadToday` / `refreshHistory` / `loadHistory` / `loadDayHours`.
- [x] Add `StepVaultRoot` with bottom tabs, local vector icons, and back handling.
- [x] Reduce Today to hero, metrics, and one hourly chart.
- [x] Build History summary, weekly totals bars, selected-week rows, and empty/cached states.
- [x] Build the hourly day-detail bottom sheet with lazy loading and retry.
- [x] Update SRS, TECH_PLAN, DEV_PLAN, and README.
- [ ] Verify static references and run tests/assemble on CI or Android Studio.

## Verification

- [x] Static XML/resource/reference validation and compile-oriented review.
- [ ] Open in Android Studio / run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Install on Xiaomi device and grant physical activity permission.
- [ ] Verify live steps match the system step counter.
- [ ] Verify both widgets in setup, active, and cached states.
- [ ] Verify 2x1 margins, metrics, large step/goal values, and enlarged font scale.
- [ ] Verify Today shows only the hourly chart and History weekly bars select a week.
- [ ] Verify a history day row opens the hourly sheet and back closes it before leaving History.
- [ ] Verify provider changes trigger a debounced widget refresh while the app is in the foreground.
- [ ] Verify goal change updates dashboard and widgets, including tapping the 4x2 goal line.
- [ ] Verify non-Xiaomi device shows provider-unavailable state.
- [ ] Build the minified release APK and record its size.
- [ ] Run lint and fix any warnings worth addressing.
- [ ] Decide whether to add an optional local body/stride profile for estimate refinement.
