# StepVault Refinement Plan

Created: 2026-11-07
Status: source implementation complete; build/device verification pending

This document is the durable plan for correctness fixes, APK size reduction,
battery/background-work optimization, and a deliberate move toward a minimal
UI/UX in both the app and the widgets. It supersedes the earlier ad-hoc
analysis in chat and should be updated as work lands.

Estimates are rough dev-day ranges. The environment used for planning has no
JDK or Android SDK, so build verification must happen through CI or Android
Studio.

---

## 0. Goals and guardrails

### Goals

1. Fix the known correctness issues in caching, progress, refresh, and
   accessibility.
2. Make the app lighter: smaller release APK, fewer dependencies, fewer unused
   resources.
3. Make background work battery-friendly: one refresh pipeline, coalesced
   queries, scoped background work, no redundant widget redraws.
4. Refine the UI/UX toward minimalism:
   - one chart visible at a time, not two stacked graphs,
   - fewer cards, fewer labels, fewer competing actions,
   - clearer hierarchy with more negative space,
   - one primary metric per widget, with compact secondary metrics where they
     already work well.
5. Keep the app private: no `INTERNET`, no analytics, no new SDKs.

### Guardrails

- Keep minSdk 30 / targetSdk 35.
- Prefer removing dependencies over adding them.
- Every behavior change gets a test.
- Do not add chart libraries, adaptive-layout libraries, or animation
  libraries; Compose and Material 3 already cover the needs.
- Update `README.md`, `docs/SRS.md`, `docs/TECH_PLAN.md`, `docs/DEV_PLAN.md`,
  and `docs/TODO.md` whenever a requirement or promised behavior changes.

---

## 1. Baseline and measurement

Before changing code, capture a baseline.

- Build `:app:assembleRelease` and record size with `apkanalyzer apk summary`
  and `apkanalyzer files list`.
- Build the debug APK and record its size separately; debug is often dominated
  by `material-icons-extended`.
- Record current lint output and unit test results.
- Record widget behavior: setup, active, cached, two instances, resize, goal
  change.
- Record a device battery baseline:
  - `adb shell dumpsys batterystats com.stepvault.app`
  - `adb shell dumpsys jobscheduler`
  - `adb shell dumpsys alarm`
- Add CI steps for release build, size report, lint, and unit tests. The current
  workflow builds debug and runs tests only.

---

## 2. Correctness fixes

### A1. Stop wiping today's cache on an empty provider result

File: `app/src/main/java/com/stepvault/app/data/StepRepository.kt:51-68`

Current behavior prefers provider data for today even when it is zero, then
upserts today's zero row and deletes today's hourly rows. A transient empty
query (reboot, provider lag, partial ROM behavior) erases today's steps.

Plan:

- Treat a successful query with no samples for today as "no provider update",
  not "today is zero".
- Only overwrite today's daily row when provider data exists, or when the query
  is confirmed authoritative for today.
- Preserve today's cached hourly rows when the provider returns no rows.
- Keep the existing behavior only if the provider explicitly returns a
  zero-step row for today.
- Add repository tests with a fake provider client and in-memory Room:
  - empty provider result keeps cached today,
  - real provider data replaces today,
  - prior-day cache fallback still works,
  - hourly rows are preserved on empty result.

### A2. Fix premature 100% progress

File: `app/src/main/java/com/stepvault/app/domain/Models.kt:71-75`

`progressPercent` rounds 7,999 / 8,000 to 100% while `remainingSteps` still
says "1 to go".

Plan:

- Truncate percent with `floor`, or return 100 only when
  `today.steps >= goal`.
- Keep the ring animation clamped at 1.0.
- Add tests for 0, 1, 7,999, 8,000, and over-goal values.
- Make widgets use the corrected percent.

### A3. Replace `onTerminate` observer cleanup

File: `app/src/main/java/com/stepvault/app/StepVaultApp.kt:38-68`

`onTerminate` is not called on real devices, so the cleanup is dead code.

Plan:

- Remove reliance on `onTerminate`.
- Register the `ContentObserver` only while the app is foreground, using
  `ProcessLifecycleOwner` (small `lifecycle-process` artifact) or a manual
  activity refcount.
- Unregister on background/process stop.
- While backgrounded, rely on the scheduled background refresh.
- Register the observer only when at least one widget exists.
- Keep the 1.5 s debounce, add a minimum refresh interval, and skip refresh if
  the snapshot signature is unchanged.

### A4. Remove duplicate refresh paths

Files:

- `app/src/main/java/com/stepvault/app/widget/BaseHealthWidget.kt:52-65`
- `app/src/main/java/com/stepvault/app/widget/WidgetUpdater.kt:15-33`
- `app/src/main/java/com/stepvault/app/work/StepSyncWorker.kt`

Today, widget `onUpdate` refreshes the repository, and `WidgetUpdater` may
refresh again. Dashboard refresh, observer, and worker can also overlap.

Plan:

- Add single-flight refresh in `StepRepository`: `Mutex` plus a short-lived
  in-memory snapshot (TTL about 15-30 s), with a `force` flag for manual
  refresh.
- `WidgetUpdater.updateAll(context, snapshot)` must never re-query when a
  snapshot is supplied.
- `BaseHealthWidget` must call one shared entry point, not its own repository
  query.
- Add a snapshot signature (`epochDay`, `steps`, `goal`, `status`, `lastSync`)
  and skip `updateAppWidget` calls when nothing changed.
- Coalesce concurrent requests so N widgets plus observer plus worker produce
  one provider query.

### A5. Remove dead code and unused resources

- Delete `StepDao.latest()` (`data/local/StepDao.kt:13`).
- Delete `Formatters.percent()` (`util/Formatters.kt:18`).
- Remove placeholder preview strings that are no longer referenced, or keep
  only those used by `previewLayout`.
- Run lint `UnusedResources` and remove the rest.

### A6. Harden the exported widget path

File: `app/src/main/AndroidManifest.xml:33,46`

The custom `miui.appwidget.action.APPWIDGET_UPDATE` action can be sent by any
app.

Plan:

- Keep the receivers exported for the system widget host.
- Decide whether the custom MIUI action is required for parity. If not, remove
  it and rely on the standard action plus background refresh.
- If kept, ensure the handler only schedules a coalesced refresh and exposes no
  data.
- Add a debounce/throttle at the refresh entry point regardless.

### A7. Add timezone and midnight tests

- Add tests for samples crossing midnight: they are assigned to the begin
  day/hour, as documented.
- Add a DST transition test if feasible.
- Add a test proving cached prior days survive partial provider ranges.

### A8. Localization cleanup

- Move remaining hardcoded unit strings in `Formatters` and UI into resources
  where practical.
- Keep numeric formatting locale-aware.
- Verify RTL does not break charts or labels.
- If only English is shipped, state that explicitly in docs.

### A9. Accessibility baseline

- Add `contentDescription` / semantics to the charts, ring, stat cards, and
  widgets.
- `WeekChart.kt` and `HourlyChart.kt` draw bars on `Canvas` with tap only.
  Add:
  - a summary semantics node,
  - per-day/hour custom actions or descriptions,
  - minimum 48 dp touch targets where possible,
  - selected-state announcements.
- Verify with TalkBack and large font scale.

---

## 3. Lightweight APK plan

### B1. Enable R8 and resource shrinking for release

File: `app/build.gradle.kts`

Current release has `isMinifyEnabled = false`. This is the largest single size
lever.

Plan:

- `isMinifyEnabled = true`
- `isShrinkResources = true`
- Keep the default optimized ProGuard file.
- Add keep rules only where needed for Room, WorkManager, and widget providers;
  manifest-referenced classes are generally kept automatically, but verify.
- Test the release build on a device/emulator, not just debug.
- Expect a substantial release reduction; measure it instead of promising a
  number.

### B2. Remove `material-icons-extended`

File: `app/build.gradle.kts`

The app uses only three icons: `Flag`, `Refresh`, and `DirectionsWalk`. The
extended icon pack contains thousands of generated icons and inflates debug
builds and build time.

Plan:

- Replace the three usages with local vector drawables rendered via
  `painterResource`, or define small `ImageVector`s in code.
- Remove `androidx.compose.material:material-icons-extended`.
- Keep `material3` for components.
- Verify tinting and RTL mirroring.
- This removes a large dependency with no loss of functionality.

### B3. Restrict shipped locales

The app only has English strings, but AndroidX libraries ship dozens of
translations.

Plan:

- Add `androidResources { localeFilters += listOf("en") }` (AGP 8.7) or the
  equivalent `resourceConfigurations`.
- Confirm `Locale.getDefault()` formatting still works.
- Expand the filter if more languages are added later.
- This is usually a meaningful resource-size reduction.

### B4. Remove other redundant build features

- Remove `vectorDrawables.useSupportLibrary` (minSdk 30 supports native
  vectors).
- Remove `debugImplementation("androidx.compose.ui:ui-test-manifest")` unless
  UI tests are added; if tests are added, it stays debug-only and does not
  affect release size.
- Evaluate whether `room-ktx` is still required with Room 2.7.2 for the
  suspend-only DAO; remove only if build and tests pass without it.
- Do not add chart, adaptive-layout, or animation libraries.

### B5. Size checks in CI

- Add `assembleRelease` and an `apkanalyzer` summary step.
- Upload the release APK and a size report artifact.
- Add a size budget check (fail if the universal release APK exceeds the
  agreed target).
- Set the target after baseline measurement. A realistic goal is a 30-40%
  release reduction plus removal of the icon pack; do not hardcode a target
  before measuring.

### B6. Optional aggressive size option

If size is the top priority:

- Drop WorkManager and rely on system widget updates plus app-open/manual
  refresh (see the battery section). This removes a dependency and its runtime.
- Keep this as a separate decision because it changes background freshness
  guarantees.

---

## 4. Battery and background-work plan

### C1. Single refresh pipeline

Implement one `StepRepository.refresh(mode)` entry point with:

- `Mutex` to prevent concurrent provider queries,
- short TTL snapshot reuse for repeat calls,
- coalescing for observer/worker/widget bursts,
- optional `force` for user-initiated refresh.

### C2. Split refresh scopes

Every refresh currently queries 7 days and aggregates hourly data for each
day.

Plan:

- `RefreshMode.FULL`: 7 days plus hourly, used on app open, manual refresh,
  goal change, and permission grant.
- `RefreshMode.TODAY`: today only plus today's hourly, used by widgets and
  background work.
- Always update the goal.
- Run cache pruning only on `FULL` refreshes, or at most once per day.
- This reduces cursor rows, aggregation work, and Room writes in the
  background.

### C3. Gate background work on widget existence

- Schedule the periodic worker only when at least one widget instance exists.
- Cancel it in `onDisabled` when the last widget is removed.
- Re-schedule in `onEnabled` / `onUpdate` when needed.
- If there are no widgets and the app is not open, there is nothing to update.

### C4. Reduce background frequency

The baseline worker ran every 15 minutes and system widget updates also ran
every 30 minutes. That was duplicate wakeups.

Recommended:

- Keep WorkManager at 30 minutes (or make it a single configurable constant)
  and set widget `updatePeriodMillis="0"` so there is one background
  scheduling path.
- Update README/SRS/TECH_PLAN to match. The 15-minute claim was removed when
  the behavior changed.

Alternative aggressive option:

- Remove WorkManager entirely and rely on `updatePeriodMillis="1800000"` plus
  the foreground observer and manual refresh. This is the smallest and most
  battery-friendly, at the cost of less guaranteed background freshness.

### C5. Foreground-only observer, throttled

- Register `ContentObserver` only while the app is foreground and widgets
  exist.
- Keep the 1.5 s debounce, add a minimum refresh interval.
- Skip refresh if the provider notification did not change the snapshot
  signature.
- Unregister when the app goes background or the last widget is removed.
- This keeps near-real-time widget updates while the user is in the app
  without background observer churn.

### C6. Reduce widget IPC and redraws

- Render once per layout and reuse the `RemoteViews` for all IDs (already
  done).
- Skip `updateAppWidget` when the snapshot signature is unchanged.
- Avoid refreshing from both `onUpdate` and `WidgetUpdater` for the same event.
- Keep widget update work on `Dispatchers.IO`.

### C7. Battery verification

- Use `dumpsys jobscheduler` to confirm one periodic job and its interval.
- Use `dumpsys batterystats` and Battery Historian to compare wakeups before
  and after.
- Test:
  - no widgets installed results in no periodic worker,
  - one widget results in one worker,
  - multiple widgets result in one query per cycle,
  - app backgrounded results in observer inactive,
  - app foregrounded results in observer active.
- Test Doze with `adb shell dumpsys deviceidle force-idle` if possible.

---

## 5. Minimal UI/UX plan

The current UI is functional and visually consistent, but it presents two
charts on one screen and several nested cards. The new direction is minimal:
one primary chart at a time, fewer surfaces, fewer labels, and clearer
hierarchy.

### D1. Information architecture

Target single-screen structure:

1. **Top bar**
   - Title: "Steps"
   - Short date or "Today"
   - One subtle refresh action (icon or overflow), no extra actions.
2. **Hero**
   - Step count as the dominant number.
   - Goal ring around or beside it, not duplicated by a second step count.
   - Goal line: "Goal 8,000" with a tappable edit affordance. No full-width
     "Set goal" button competing with the hero.
3. **Metrics strip**
   - One compact row: Distance, Calories, Active.
   - Use whitespace and dividers, not three separate cards.
4. **Insights card**
   - One chart only, switched by a segmented control: `Week` and `Hourly`.
   - Default to `Week`.
   - Selecting a week bar updates a single detail line under the chart.
   - Switching to `Hourly` shows the selected day's 24 buckets.
   - Tapping an hour updates a single detail line or pill.
5. **Footer**
   - One subtle line: source and last updated time.
   - Move extra explanation into an info sheet or settings, not the main
     scroll.

Remove:

- the second chart from the main scroll,
- the separate nested chart cards,
- the large bottom goal button,
- redundant labels and helper copy that repeat the same information.

### D2. Chart interaction

- Use a two-option segmented control above the chart: `Week` | `Hourly`.
- Animate the switch with `AnimatedContent`; keep only one chart composable
  active.
- Week chart:
  - tap selects a day and updates the detail line,
  - selected bar is accented, others are muted,
  - today is marked distinctly,
  - goal line has a small label,
  - zero-data week shows a compact empty message.
- Hourly chart:
  - tap selects an hour and updates one detail pill,
  - current hour is marked for today,
  - non-selected hours are slightly dimmed,
  - no permanent multi-line footer.
- Do not auto-switch tabs when a day is selected; preserve user intent.
- Add haptic feedback on selection where supported.
- Add semantics so TalkBack can read each bar and select it.

### D3. Visual system

- Dark-first, minimal surfaces.
- Reduce card count: one hero surface, one insights surface, one compact
  metrics area. Prefer dividers and spacing over stacked rounded cards.
- Soften contrast between background and surface; keep accent only for
  progress, selection, and goal.
- Standardize spacing on a 4/8 dp grid via a new `Dimens.kt`.
- Standardize radii via `Shapes.kt`.
- Reduce the number of text styles; use tabular figures for numbers.
- Avoid decorative gradients beyond the ring and progress bar.
- Keep motion subtle: ring progress, chart selection, count-up animation.
  Respect system animation scale.

### D4. State handling

- Add pull-to-refresh using the existing Material 3 API; no new dependency.
- Replace the thin loading bar with a small top-bar spinner or skeleton, and
  reserve layout space so content does not jump.
- Show one compact message for error/setup/permission states with a single
  primary action and an optional secondary "Open settings".
- Use a snackbar for transient refresh failures/successes.
- Animate transitions between loading, ready, cached, and error states.
- Keep cached data visible when a refresh fails; show a subtle "Cached" chip
  instead of replacing the whole screen.

### D5. Goal editing

- Use a bottom sheet or compact dialog.
- Preset chips: 5,000 / 8,000 / 10,000 / 12,000.
- Stepper buttons plus manual input with validation.
- Save is disabled until the value is valid.
- Tapping the goal line in the hero or widget opens this editor.
- Ensure it scrolls at large font scales.

### D6. Responsive layout and accessibility

- Use `BoxWithConstraints` to switch to a two-column layout on wide screens:
  hero and metrics on the left, the single chart on the right.
- Cap ring size relative to available width.
- Test 320 dp width, tablet width, 200% font scale, RTL, and TalkBack.
- Ensure all interactive elements are at least 48 dp.
- Add content descriptions and live-region semantics for loading/error states.
- Avoid clipped text in the metrics strip; allow wrap or adjust type size.

### D7. Widget minimal redesign

4x2 widget:

- One dominant step count.
- One line: "Goal 8,000 · 74%" with the goal text tappable to open the goal
  editor.
- One progress bar.
- One thin secondary line: distance and calories.
- Remove visible "Details" and "Set goal" text buttons; the whole card opens
  the app, and the goal line opens the editor.
- Cached state: show a small "Cached" label in place of the percent, not a
  full-screen state.
- Setup state: icon, one-line explanation, whole-card tap opens the app. No
  separate button.
- Use tabular figures and fewer colors.

2x1 widget:

- The current 2x1 layout is approved and stays structurally the same:
  icon, "Steps" plus `/ goal` text, auto-sized step count, thin progress bar,
  and the compact distance/calories row.
- Keep the small top/bottom outer margin and full-width card.
- Do not remove the secondary metrics row.
- Apply only polish: tabular figures, slightly stronger hierarchy between the
  step count and secondary text, consistent accent usage, and a subtle
  "Cached" label when data is stale.
- Whole card opens the app.
- Setup/cached states keep the existing compact shape.

Widget continuity:

- Match app tokens for spacing, radii, and accent.
- Keep the app's dark surface/black card identity.
- Use content descriptions for the icon.
- Verify large values, font scale, and the 2x1 outer margins.

### D8. Onboarding and permission states

- One clear access card with a single primary action.
- Explain the MIUI provider and the non-Xiaomi fallback in one sentence.
- After permission grant, refresh and show a confirmation snackbar.
- If MIUI denies provider access, show the exact next steps and a retry
  action.
- Avoid dead ends when access is blocked and no cache exists.

### D9. Motion and polish

- Ring progress animation on load/refresh.
- Count-up animation for the hero number.
- Animated chart selection and tab transition.
- No infinite or decorative animations.
- Respect reduced motion and system animation scale.
- Keep widget rendering static; animations belong to the foreground app only.

---

## 6. Testing and verification plan

### Unit tests

Repository:

- empty provider result keeps cached today,
- today-only vs full refresh modes,
- coalescing and TTL behavior,
- cache pruning boundaries,
- goal changes.

Aggregator:

- samples crossing midnight,
- DST transition,
- percent truncation boundaries,
- zero/negative durations.

Formatters:

- locale-specific number and date output,
- large values.

### UI tests (androidTest, test-only dependencies)

- Goal dialog validation and presets.
- Permission/setup/error states render the right actions.
- Week/hour chart selection updates the summary.
- Chart tab switch keeps only one chart composed.
- Large font scale and RTL smoke tests.

### Widget verification

- Setup state with no cache.
- Active state with data.
- Cached state after a forced provider error.
- Two widget instances update once per refresh.
- Goal change updates both widgets.
- 2x1 margins and large values.
- Optional Robolectric or instrumented `RemoteViews` assertions.

### Build and size verification

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintRelease`
- `./gradlew :app:assembleRelease`
- `apkanalyzer apk summary` and `apkanalyzer files list`
- Compare baseline vs final size and document the delta.
- Confirm the release build works with R8, Room, widgets, and WorkManager.

### Battery verification

- Confirm one periodic job when widgets exist and none when they do not.
- Confirm refresh coalescing with debug-only counters/logging.
- Confirm observer stops in background.
- Compare `dumpsys batterystats` before and after.
- Test Doze behavior.

### Documentation updates

Update:

- `README.md`
- `docs/SRS.md`
- `docs/TECH_PLAN.md`
- `docs/DEV_PLAN.md`
- `docs/TODO.md`

Required updates:

- refresh interval/mode,
- observer lifecycle,
- size strategy,
- minimal UI structure and single-chart behavior,
- widget changes: 4x2 minimal layout and visible-action decisions, plus the
  preserved 2x1 structure and its polish-only changes,
- any requirement changes (for example 15-minute to 30-minute background
  refresh, or WorkManager removal if chosen).

---

## 7. Execution order and effort

| Phase | Work | Effort |
| --- | --- | --- |
| 0 | Baseline build, size, lint, battery, CI size report | 0.5 day |
| 1 | Correctness: A1, A2, A5, A7, repository tests | 1-1.5 days |
| 2 | Refresh architecture: coalescing, scopes, observer lifecycle, WorkManager gating | 1.5-2 days |
| 3 | Size: R8/shrink, remove icons, locale filter, dead resources, CI size checks | 0.5-1 day |
| 4 | Minimal UI shell: tokens, hero, metrics strip, single chart card | 1.5-2 days |
| 5 | Chart tabs, goal sheet, state handling, responsive/accessibility | 1.5-2 days |
| 6 | 4x2 minimal redesign, 2x1 polish-only, and widget testing | 1 day |
| 7 | Verification, battery measurement, docs | 1 day |

Total: roughly 7-10 dev days.

Recommended order: phases 1-2 first (behavior and tests), then 3 (size), then
4-6 (UI and widgets), then 7.

---

## 8. Acceptance criteria

### Correctness

- Empty provider results never erase today's cached steps or hourly rows.
- Percent reads 100 only at or above the goal.
- Overlapping refresh triggers produce one provider query.
- Midnight and DST behavior are covered by tests.

### Size

- Release build is minified and resource-shrunk.
- `material-icons-extended` is removed.
- Non-English AndroidX resources are filtered out.
- Release APK is at least 30-40% smaller than baseline, or meets the agreed
  target.
- CI reports size and fails on regression.

### Battery

- No periodic background work when no widgets exist.
- Background refresh runs at most once per chosen interval (recommended 30
  minutes).
- No duplicate refresh within the coalescing window.
- Observer is foreground-only and throttled.
- Widget IPC is skipped when data is unchanged.

### UI/UX

- Only one chart is visible at a time; `Week` is the default tab.
- The dashboard has no nested chart cards and no large bottom goal button.
- Metrics appear as one compact strip, not three separate cards.
- All loading/ready/cached/error/setup states are clear and actionable.
- Charts are tappable, animated, and TalkBack-readable.
- Goal editing supports presets and validation.
- Layout works at small widths, tablet widths, 200% font scale, and RTL.
- Widgets keep one primary step metric and one progress affordance.
- The 2x1 widget keeps its approved structure: goal, progress, and the compact
  distance/calories row.
- Widget setup/cached states use one short label and a whole-card tap.

---

## 9. Decisions

1. **Background refresh strategy** - decided
   - WorkManager at 30 minutes, `updatePeriodMillis="0"`, gated on widgets.
   - WorkManager is cancelled when the last widget is removed.

2. **2x1 widget metrics** - decided
   - Keep the current 2x1 structure, including distance/calories.
   - Limit changes to typography, color/token alignment, tabular figures, and
     Cached-state polish. No layout redesign.

3. **Widget actions** - decided
   - Remove visible "Details" and "Set goal" text buttons; the whole card opens
     the app and the goal line opens the goal editor.

4. **Theme scope** - decided
   - Keep dark-only with refined brand tokens; no dynamic color.

5. **Observer scope** - decided
   - Foreground-only observer. Near-real-time widget updates only while the app
     is in use.

6. **Size target** - pending measurement
   - Confirm a concrete release APK budget after baseline measurement. A
     realistic starting goal is a 30-40% reduction.

7. **Test depth** - partially decided
   - Unit tests are implemented. Compose UI and widget instrumentation tests
     remain optional; screenshot/performance tests do not affect release size.
