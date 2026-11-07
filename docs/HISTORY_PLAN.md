# History Tab and Home Hourly Redesign Plan

Status: implemented, pending build/device verification
Created: 2026-11-07

This plan adds a dedicated History destination and simplifies the Today screen
to a single hourly view. It keeps the existing dark, minimal visual language,
the current widgets, and the no-network/no-analytics constraints.

Recommended defaults:

- Slim bottom navigation with two tabs: `Today` and `History`.
- Today shows the hero ring, compact metrics, and a single hourly chart.
- History shows a 60-day summary, weekly totals, a selected-week day list, and
  a day-detail bottom sheet with hourly steps.
- History window and cache retention are both 60 days.
- No new navigation or chart dependencies.

---

## 1. Principles

1. One primary job per screen:
   - Today: how am I doing right now, and how did today unfold by hour?
   - History: how have my days and weeks gone, and what did a specific day
     look like by hour?
2. Keep one chart visible at a time. No stacked graphs on Today.
3. Prefer one surface per section and dividers over nested cards.
4. Use the existing accent, dark surfaces, tabular figures, and subtle motion.
5. Keep all provider access in `StepRepository`; widgets continue to use the
   today-only path.
6. Do not add `navigation-compose`, chart libraries, or adaptive-layout
   libraries.

---

## 2. Navigation and information architecture

```text
StepVaultRoot
├── Today tab
│   ├── Top bar: Today, date, refresh
│   ├── Status card (conditional)
│   ├── Goal ring + tappable goal line
│   ├── Compact metrics strip
│   ├── Hourly steps card for today
│   └── Footer
└── History tab
    ├── Top bar: History, "Last 60 days", refresh
    ├── Summary card: total, daily average, best day
    ├── Weekly totals card: one bar per week
    ├── Selected-week day list
    │   └── Day row -> hourly detail bottom sheet
    └── Footer / empty or cached state
```

### Navigation behavior

- Use a slim custom bottom bar, not the full-height stock `NavigationBar`.
  Height about 60 dp, two equal tabs, accent pill behind the selected tab,
  labels always visible.
- Tab state is saved with `rememberSaveable`.
- Back behavior:
  - Day-detail sheet open -> close the sheet.
  - History tab -> switch to Today.
  - Today tab -> normal system back.
- Switching to a tab calls `onVisible()` on that screen's ViewModel. A refresh
  only runs if its TTL has expired.

### Why bottom tabs

Two top-level destinations are easier to discover with a persistent bottom
switcher. A two-item bottom bar also avoids adding a navigation graph for a
single split. The bar should feel light: no elevation shadow, no large labels.

---

## 3. Today screen

Purpose: show today's progress and today's hourly rhythm, without the current
`Week | Hourly` switch.

### Layout

1. **Top bar**
   - Title: `Today`
   - Subtitle: formatted date, or `Today, 7 Nov` if preferred.
   - One refresh icon; disabled while loading.
2. **Status card (conditional)**
   - Reuse the existing `AccessCard` states for permission, provider missing,
     and error.
3. **Hero**
   - Existing `StepRing`, about 200–220 dp.
   - Step count is the dominant number; goal line stays tappable and opens the
     goal dialog.
   - Remaining steps or `Goal reached` under the ring.
4. **Metrics strip**
   - Distance, calories, active time in one compact surface with dividers.
5. **Hourly card**
   - Title: `Hourly`
   - Subtitle: `Today`
   - Reuse `HourlyChart` with:
     - 24 bars,
     - current hour highlighted by default,
     - tap-to-select,
     - one detail line for the selected hour,
     - empty state when there are no steps yet.
   - No week chart and no week/hourly switch on Today.
6. **Footer**
   - Source and last-updated line; cached state as today.

### Interaction

- Pull to refresh keeps current content visible.
- Hour selection is local UI state and does not trigger a provider query.
- Goal line opens the existing goal dialog.
- Leave space for a future "open full day" action, but do not add one now.

---

## 4. History screen

Purpose: show every day in the 60-day history window, organized by week, with a
weekly overview and hourly detail on demand.

### 4.1 Summary card

One surface with three metrics:

- `Total`: sum of steps in the window.
- `Daily average`: total divided by elapsed days in the window.
- `Best day`: highest step count with a short date under it.

Typography: total is the large number; average and best are secondary. Use
tabular figures and keep the card compact.

### 4.2 Weekly totals card

- One bar per ISO week in the window, oldest to newest or newest to oldest
  depending on the rest of the screen. Recommended: oldest to newest inside the
  chart, with the current week selected by default.
- Bar height uses the week's total steps.
- Selected week uses the accent; other weeks use a muted accent.
- Optional weekly goal line: `goal * elapsedDaysInWeek`, dashed and subtle.
- Tap a bar to select that week and update the day list.
- Custom accessibility actions for previous/next week.
- Limit labels to week-start day/month, shown for every other bar if space is
  tight.

### 4.3 Selected-week day list

Header: `3 Nov – 9 Nov` plus the week's total steps.

Rows, newest first:

```text
Mon 3    8,420 steps        5.1 km       [progress bar]
Tue 4    3,120 steps        1.9 km       [progress bar]
...
```

- Each row is at least 48 dp tall.
- Show steps as the primary value; distance as secondary.
- A thin progress bar compares the day to the daily goal.
- The current day is marked with a small dot or label.
- Tapping a row opens the day-detail sheet.
- Empty days appear with `0 steps` and a muted progress bar, so the week still
  reads as a complete set of days.
- For the current week, show only elapsed days; do not invent future days.

### 4.4 Empty and cached states

- No history: one compact card explaining that steps will appear after the
  first successful sync, with a retry action.
- Cached history: keep the data visible and show a small `Cached` label in the
  summary card or footer.
- Permission/provider errors: keep cached days and show one compact status card
  above the summary.

---

## 5. Day-detail bottom sheet

Open from any history day row, and optionally from a future Today "full day"
action.

### Layout

- Material 3 `ModalBottomSheet`, 24 dp top corners, dark surface.
- Drag handle.
- Header: full date, e.g. `Wed, 8 Oct`.
- One metrics strip: steps, distance, calories, active time. Keep it to one row
  if space allows; otherwise two rows.
- Hourly chart for that date, same chart component as Today.
- A small goal line: `Goal 8,000 · 74%`.
- Close affordance through swipe or the system back action.

### Data behavior

- On open, show cached hourly rows immediately if present.
- If hourly rows are missing, query the provider for that single day, aggregate
  hourly, upsert, and show the chart. Cache the result in the ViewModel and in
  Room.
- Failure falls back to cached data or the empty chart with a retry action.
- Default selected hour: current hour for today, noon for past days.

---

## 6. Visual system

Use the existing dark palette and accent, with fewer surfaces.

### Surfaces

- Background: existing ink/black.
- Surface: existing elevated dark surface.
- Cards: 24 dp radius, 18–20 dp inner padding.
- Dividers: low-contrast outline at about 40–50% alpha.
- Accent: existing orange for selected hours, selected week, progress, and the
  goal line only.

### Spacing

- Screen horizontal padding: 20 dp.
- Section gap: 16 dp.
- Inside cards: 18–20 dp.
- Use a 4/8 dp grid for small gaps.
- Bottom content padding includes the bottom bar and navigation bar insets.

### Typography

- Step numbers: large, medium weight, `tnum`.
- Labels: `labelSmall` or `labelMedium`, secondary color.
- Section titles: `titleMedium`.
- Avoid more than three text styles in one card.
- Use auto-sizing for the hero number and history total.

### Motion

- Ring progress and step count on load/refresh.
- Weekly bar selection color/height transition, 180–220 ms.
- Bottom sheet uses the standard Material motion.
- No infinite, decorative, or looping animation.
- Respect the system animation scale by using Compose animation APIs.

### Navigation bar

- Height about 60 dp plus navigation-bar inset.
- Two equal tab items, icon over label or icon + label in one row.
- Selected: accent pill behind the content; unselected: secondary text.
- No shadow; a 1 dp top divider is enough.
- New local vector icons: `ic_today`, `ic_history`; keep the existing icon
  style.

---

## 7. Data model and repository

### 7.1 New domain models

```kotlin
data class WeekSummary(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<DaySteps>,
    val totalSteps: Int,
    val averageSteps: Int,
    val goalDays: Int,
)

data class HistorySnapshot(
    val days: List<DaySteps>,
    val weeks: List<WeekSummary>,
    val goal: Int,
    val status: StepStatus,
    val message: String?,
    val lastSync: Long?,
)

data class DayHours(
    val date: LocalDate,
    val hours: List<HourlySteps>,
    val status: StepStatus,
    val message: String?,
)
```

Keep `DaySteps` and `HourlySteps` unchanged. Add a pure `HistoryAggregator` or
extend `StepAggregator` with week grouping so it can be unit-tested without
Android.

### 7.2 Week rules

- Weeks are ISO weeks: Monday through Sunday.
- The current week ends today, not Sunday.
- Days are ordered oldest-to-newest for aggregation, newest-first for display.
- Zero-fill missing dates in the history window.
- `goalDays` counts days with `steps >= goal`.
- Weekly average divides by the number of elapsed days in that week.
- Weekly goal line, if shown, is `goal * elapsedDaysInWeek`.

### 7.3 Refresh modes

Replace the current broad `FULL` refresh with two explicit paths:

```text
Today path
  refreshToday(force): StepSnapshot
  loadToday(): StepSnapshot

History path
  refreshHistory(force): HistorySnapshot
  loadHistory(): HistorySnapshot
  loadDayHours(date, force): DayHours
```

- `TODAY`: today's daily row and today's hourly rows; used by Today and
  widgets. Keep the current 20 s coalescing TTL.
- `HISTORY`: the 60-day daily window; used by History. No hourly aggregation
  for every day.
- `loadDayHours`: read hourly rows from Room first; query one day only when the
  rows are missing or a forced retry is requested.

Keep a single repository mutex so Today, History, widgets, and the worker
cannot issue overlapping provider queries. Use separate freshness timestamps
for the Today and History snapshots; History can use a 5-minute TTL.

### 7.4 History refresh

- Query start: `today.minusDays(HISTORY_DAYS - 1).atStartOfDay(zone)`.
- Query end: tomorrow at start of day.
- Aggregate daily totals only.
- Upsert days that have provider data.
- Preserve cached days when the provider returns no rows for them.
- Prune daily and hourly rows older than `CACHE_DAYS` once per day on a History
  refresh.
- Build a zero-filled 60-day list for the UI from provider rows plus cache.
- `HISTORY_DAYS = 60`, `CACHE_DAYS = 60` initially.

If "all time" is later required, raise both constants and add horizontal
paging; do not load an unbounded range into one screen.

### 7.5 Cache and goal behavior

- Goal changes update both Today and History state. History only needs to
  re-emit its existing days with the new goal; it does not require a provider
  query.
- A History refresh that includes today may update the shared today cache.
  Widgets can continue to use their 30-minute path; an immediate widget redraw
  is optional and should not trigger a second provider query.
- Keep the existing empty-provider cache preservation rules.

### 7.6 Simplify `StepSnapshot`

After the Home week chart is removed:

- Remove `week` from `StepSnapshot`.
- Keep `today`, `hours`, `goal`, `status`, `message`, and `lastSync`.
- Rename `hoursByDay` to `hours` for the Today screen.
- Remove `RefreshMode.FULL`.
- Update repository tests and docs accordingly.

This avoids carrying an unused seven-day payload on every Home refresh.

---

## 8. ViewModels and state

### 8.1 Root

```text
StepVaultRoot
  tab: Today | History (rememberSaveable)
  todayViewModel
  historyViewModel
```

### 8.2 TodayViewModel

Rename `StepViewModel` to `TodayViewModel` only if it keeps the diff readable.
It owns:

- `TodayUiState(loading, refreshing, snapshot, error, showGoalDialog)`.
- `onVisible()`, `refresh(force)`, `setGoal`, `showGoalDialog`.
- `hour` selection can remain local to the chart.

### 8.3 HistoryViewModel

```kotlin
data class HistoryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: HistorySnapshot? = null,
    val selectedWeekStart: LocalDate? = null,
    val selectedDate: LocalDate? = null,
    val selectedHours: List<HourlySteps> = emptyList(),
    val hoursLoading: Boolean = false,
    val showDaySheet: Boolean = false,
    val error: String? = null,
)
```

Actions:

- `onVisible()`: load cached history, refresh if the 5-minute TTL expired.
- `refresh(force)`.
- `selectWeek(start)`.
- `selectDay(date)`: show sheet, load cached hours, lazy fetch if needed.
- `dismissDay()`.
- `retryHours()`.

### 8.4 Root wiring

- `MainActivity` creates the root composable and both ViewModels through
  factories.
- Tab changes call `onVisible()` and do not recreate ViewModels.
- Goal dialog lives on Today; after save, ask History to re-emit its data with
  the new goal.

---

## 9. Performance and battery

- History refresh is on-demand: tab open, pull-to-refresh, or manual refresh.
- Do not add WorkManager history work.
- Keep widget refresh and background work unchanged.
- Aggregate hourly only for today and for a day the user opens.
- Use Room as the primary history source; the provider query fills the cache.
- Keep provider queries on `Dispatchers.IO`.
- Coalesce overlapping refresh requests and skip redundant widget renders as
  today.
- A 60-day daily query is acceptable on tab open; avoid doing it on every
  resume.
- Reuse one `RemoteViews` for all widget instances; no widget changes are
  required.

---

## 10. Accessibility and edge cases

- Bottom tabs expose selected state and labels; minimum 48 dp touch targets.
- Weekly bars expose a summary description and previous/next week custom
  actions.
- Day rows expose date, steps, and distance; row semantics should merge.
- Bottom sheet is dismissible with back and exposes a close action.
- Large font scale: auto-size summary/total values; allow labels to wrap;
  verify the bottom bar and day rows at 200%.
- RTL: use start/end padding; verify chart labels and week direction.
- Midnight: current day/week updates on next refresh; no crash.
- DST: use `LocalDate` and zone-aware start-of-day conversions.
- Empty week and empty day states are explicit.
- Cached state remains visible during errors.
- 60 days is a rolling window; a day beyond the window is not shown until the
  cache window is extended.

---

## 11. Implementation phases

| Phase | Work |
| --- | --- |
| 0 | Confirm the 60-day window, bottom tabs, and day-sheet decisions; add strings and icons. |
| 1 | Add `WeekSummary`, `HistorySnapshot`, `DayHours`, and `HistoryAggregator`; tests for ISO weeks, partial current week, zero fill, averages, goal days, and DST. |
| 2 | Refactor repository into Today/History paths; add lazy day-hour loading; update cache preservation and pruning; tests. |
| 3 | Add `StepVaultRoot`, bottom tabs, back handling, and shared top-bar shell. |
| 4 | Refactor Today: remove week chart and switch; add the hourly card; keep ring, metrics, goal, status, footer. |
| 5 | Build History: summary, weekly totals, selected-week day list, empty/cached states. |
| 6 | Build the day-detail bottom sheet with hourly chart and metrics. |
| 7 | Polish: spacing, auto-size, tabular figures, motion, accessibility, large font, RTL, TalkBack. |
| 8 | Update docs, tests, and static checks; manual device verification. |

---

## 12. File-by-file change map

New:

- `app/src/main/java/com/stepvault/app/domain/HistoryModels.kt`
- `app/src/main/java/com/stepvault/app/domain/HistoryAggregator.kt`
- `app/src/main/java/com/stepvault/app/ui/StepVaultRoot.kt`
- `app/src/main/java/com/stepvault/app/ui/history/HistoryScreen.kt`
- `app/src/main/java/com/stepvault/app/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/stepvault/app/ui/history/HistoryUiState.kt`
- `app/src/main/java/com/stepvault/app/ui/history/DayDetailSheet.kt`
- `app/src/main/java/com/stepvault/app/ui/components/WeeklyOverview.kt`
- `app/src/main/java/com/stepvault/app/ui/components/HistoryDayRow.kt`
- `app/src/main/res/drawable/ic_today.xml`
- `app/src/main/res/drawable/ic_history.xml`
- `app/src/test/java/com/stepvault/app/domain/HistoryAggregatorTest.kt`

Modified:

- `app/src/main/java/com/stepvault/app/MainActivity.kt`: host `StepVaultRoot`.
- `app/src/main/java/com/stepvault/app/data/StepRepository.kt`: Today/History
  paths, lazy hours, simplified snapshot.
- `app/src/main/java/com/stepvault/app/domain/Models.kt`: remove `week`, rename
  hourly payload.
- `app/src/main/java/com/stepvault/app/ui/StepScreen.kt`: become the Today
  screen content or split into `TodayScreen.kt`.
- `app/src/main/java/com/stepvault/app/ui/StepViewModel.kt`: Today-only state
  and visibility hook.
- `app/src/main/java/com/stepvault/app/ui/components/HourlyChart.kt`: optional
  compact/full-day presentation flag; reuse for Today and sheet.
- `app/src/main/java/com/stepvault/app/ui/components/Cards.kt`: reuse access,
  error, and goal components; no structural change required.
- `app/src/main/res/values/strings.xml`: tab labels, history labels, summary,
  day sheet, empty states, accessibility.
- `app/src/main/java/com/stepvault/app/widget/*`: no functional change;
  widget repository calls move to `refreshToday`.
- `app/src/test/java/com/stepvault/app/data/StepRepositoryTest.kt`: update for
  Today/History APIs and lazy hours.
- Docs: README, SRS, TECH_PLAN, DEV_PLAN, TODO.

Not changed:

- Widget layouts, widget metadata, widget refresh cadence, and widget actions.
- Permissions, privacy model, Room database version, and release shrinking.
- WorkManager interval and foreground-only observer rules.

---

## 13. Test plan

### Unit tests

- ISO week grouping, including a week spanning a month and year boundary.
- Current week ends today; future dates are not zero-filled.
- History zero-fill for missing cached days.
- Weekly total, average, and goal-day counts.
- History refresh query window and day ordering.
- Empty provider result preserves cached history.
- Pruning removes rows older than 60 days.
- Lazy day-hour load uses cache first and queries once when missing.
- Goal change updates history goal without a provider query.
- Existing Today, widget, and progress tests still pass.

### Compose/UI tests (only if the test dependency is added)

- Bottom tab switch and back behavior.
- Weekly bar selection changes the day list.
- Day row opens the sheet and displays hourly data.
- Empty/cached/error states render the right action.
- Large font and RTL smoke tests.

### Manual verification

- Today shows only today's hourly chart.
- History shows 60 days grouped by week; weekly totals match the day sums.
- Tapping a week updates the list; tapping a day opens hourly detail.
- Empty days show zero and do not crash.
- Cached history remains readable after a provider error.
- Goal changes update Today, History, and widgets.
- Rotation preserves the active tab, selected week, and sheet state.
- Back from History returns to Today; back from the sheet closes it first.
- Performance: no visible jank when opening History with 60 cached days.

---

## 14. Acceptance criteria

- Today has no week chart and no Week/Hourly switch.
- Today shows today's steps, goal ring, compact metrics, and a tappable
  hourly chart.
- History is a separate destination reachable from a persistent tab.
- History shows all 60 cached days grouped by ISO week, with zero-filled empty
  days.
- History has a weekly totals representation and a day list.
- Selecting a day shows an hourly representation for that day.
- Empty, cached, permission, and error states are clear and actionable.
- The UI remains dark, minimal, and free of nested chart cards.
- No new heavyweight dependencies, no network permission, and no widget
  regressions.
- Back, rotation, large font, RTL, and TalkBack behavior are verified.

---

## 15. Confirmed decisions

1. **History window:** 60 days, matching the cache.
2. **Navigation:** slim bottom tabs.
3. **Hourly detail:** bottom sheet.
4. **Weekly overview:** weekly totals bars.
5. **History refresh:** on tab open with a 5-minute TTL, plus manual refresh and
   pull-to-refresh.
