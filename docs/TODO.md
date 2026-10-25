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
- [x] Compose dashboard: ring, stats, interactive week chart, hourly chart, access card, goal dialog.
- [x] 4x2 and 2x1 widgets with setup/active states, click actions, resize refresh, and compact metrics.
- [x] WorkManager 15-minute sync and in-process ContentObserver widget refresh.
- [x] Hourly aggregation, Room cache, and selected-day UI.
- [x] Unit tests for aggregation, hourly buckets, formulas, and progress.
- [x] README/docs updated for interactive charts, hourly data, refresh behavior, and provider/profile limits.

## Verification

- [x] Static XML/resource/reference validation and compile-oriented review.
- [ ] Open in Android Studio / run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Install on Xiaomi device and grant physical activity permission.
- [ ] Verify live steps match the system step counter.
- [ ] Verify both widgets in setup, active, and cached states.
- [ ] Verify 2x1 margins, metrics, large step/goal values, and enlarged font scale.
- [ ] Verify week and hour selection updates summaries.
- [ ] Verify provider changes trigger a debounced widget refresh while the app is open.
- [ ] Verify goal change updates dashboard and widgets.
- [ ] Verify non-Xiaomi device shows provider-unavailable state.
- [ ] Run lint and fix any warnings worth addressing.
- [ ] Decide whether to add an optional local body/stride profile for estimate refinement.
