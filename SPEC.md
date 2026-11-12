# Pegelstand — water tracker (Android, native)

A gift app for one person. She already tracks her water; this makes it one tap and pleasant.
**The home-screen widget is the product.** The app exists for history and settings.
App name "Pegelstand" (decided by the user; the earlier working title "Schluck" is rejected and
must not appear anywhere, including class names, theme names and resource names), package `app.pegelstand`. UI language: German only. Code and comments: English.

## Non-goals (v1)

No account, no backend, no network permission, no analytics, no drink types, no editing of past
days, no Wear OS, no multi-language. Do not add features that are not in this file.

## Toolchain

- Machine-specific facts (SDK and JDK paths, which emulator to use and which one never to touch,
  where verification screenshots go) are in `LOCAL.md` next to this file. It is not tracked in
  git. Read it before the first Gradle or adb call; its emulator rules are hard rules.
- Build with JDK 21.
- Kotlin, Jetpack Compose (Material 3), classic RemoteViews for the widget, Room (KSP) for storage,
  DataStore Preferences for settings. Stable releases only, no alpha/beta/rc. The versions in use
  are in the version catalog `gradle/libs.versions.toml`.
- compileSdk/targetSdk 36, minSdk 26. Single module `app`. No DI framework: one hand-written
  `AppContainer` owned by the `Application` class.
- No WorkManager dependency of our own (see Scheduling).

## Domain rules

All of this is pure Kotlin in `app.pegelstand.domain`, no Android imports, covered by JVM unit tests.

**Logical day.** A day starts at 04:00 local time, so a glass at 01:30 counts for the evening
before. `logicalDay(instant, zone) = (instant in zone − 4h).toLocalDate()`. Stored as epoch day.
The 04:00 boundary is a constant, not a setting.

**Intake.** One tap adds `mlPerTap` (setting, default 250, range 50–1000 in steps of 50).
There is exactly one tap size; no second button.

**Goal.** `dailyGoalMl` (default 2000, range 1000–5000 in steps of 250). The goal that applied on
a day is stored with that day, so changing the goal later does not rewrite history. Changing the
goal updates today's stored goal.

**Water colour.** `waterColor` setting, one of six presets, default `BLUE`. An enum
`WaterColor(id, light, deep)` in the domain package (ARGB as plain `Int`, no Android imports):

| id | German label | light → deep |
|---|---|---|
| `BLUE` | Wasserblau | `#5CC8F5` → `#1E88E5` |
| `TURQUOISE` | Türkis | `#5FE0D0` → `#00A99D` |
| `MINT` | Minze | `#8EE6A8` → `#2EAD6B` |
| `LAVENDER` | Lavendel | `#C3B1F5` → `#7E57C2` |
| `PINK` | Rosa | `#F8A8CF` → `#E0559A` |
| `CORAL` | Koralle | `#FFB199` → `#F4664A` |

Presets only, no free colour picker. No yellow/gold preset: gold is reserved for "goal reached".
The colour applies to the water in the widget and in the app, and to the bars/drops in the
history that are not gold. The "today" state carries the current `WaterColor`, and changing it
refreshes the widgets.

**Bottle direction.** `bottleMode` setting: `FILL` (default) or `DRAIN`.

- `FILL`: the bottle starts the day empty and fills with what was drunk; shown level = total / goal.
- `DRAIN`: the bottle starts the day full, like a real bottle holding the daily goal, and empties
  as she drinks; shown level = 1 − total / goal. Reaching the goal is an empty bottle in the
  gold "reached" state.

One pure function `shownFill(totalMl, goalMl, mode)` (clamped to 0..1) is the only place that
knows the direction; widget and app both use it, so the widget animation falls when she drinks in
`DRAIN` mode without further logic. Everything else is unaffected: the amount text stays
"drunk / goal", history, streak and reminders count what was drunk. Changing the mode refreshes
the widgets statically.

**Undo.** Removes the most recent intake of the current logical day. Nothing happens if there is none.

**Streak.** Number of consecutive days with `total ≥ goal`, ending yesterday, plus today if today
is already reached. A day that is not reached yet today does not break the streak. Also: longest
streak ever.

**Reminders.** They depend on the water level. Someone who is on track is never notified.

- Active window per day: `remindFrom`–`remindUntil` (settings, default 08:00–22:00; from ≥ 04:00,
  until ≤ 23:59, until − from ≥ 2 h).
- Expected intake at time t: `goal × clamp((t − from) / (until − from), 0, 1)`.
- Deficit = expected − total. A reminder is due only if **all** hold:
  - reminders enabled, t inside the window, goal not reached
  - deficit ≥ goal / 4
  - ≥ 120 min since the last reminder
  - ≥ 60 min since the last intake (she is already catching up)
  - fewer than 4 reminders so far on this logical day
- Two pure functions: `isReminderDue(state, now): Boolean` and
  `nextCheckTime(state, now): Instant?`, the earliest future instant at which a reminder could
  become due (solve the deficit inequality for t, then apply the two minimum gaps; `null` if none
  left today). Constants live in one place.

## Data

- Room: `intake(id, timestampMs, day, amountMl)`, `day_goal(day PK, goalMl)`.
  Day totals are `SUM(amountMl) GROUP BY day`. Exposed as `Flow`.
- DataStore: `dailyGoalMl`, `mlPerTap`, `waterColor`, `bottleMode`, `remindersEnabled` (default true),
  `remindFrom`, `remindUntil`, plus reminder bookkeeping (`lastReminderAtMs`, `remindersSentDay`, `remindersSentCount`).
- One `WaterRepository` is the only write path. `addIntake()` and `undoLast()` each do, in order:
  write, refresh all widgets, cancel a showing reminder notification, reschedule the next check.
  Widget, notification action and app all call these two functions; none of them writes on its own.

## Scheduling

AlarmManager, inexact (`setAndAllowWhileIdle`), no exact-alarm permission. One pending alarm at a
time: `min(nextCheckTime, next 04:00)`. When it fires: refresh widgets (day rollover), show a
reminder if `isReminderDue`, schedule the next one. Reschedule also on boot, app update,
time/time-zone change, and settings change. Reason for alarms instead of WorkManager: the app is
rarely opened, and background jobs of rarely opened apps are deferred for hours.

## Widget (classic RemoteViews, no Glance)

The widget is a plain `AppWidgetProvider` with XML layouts. Glance was dropped because it cannot
deliver animation frames (it publishes at most a few uneven updates per second and wraps the
layout per size, so frames pushed beside it are ignored).

- Default 2×2, resizable both ways. Must look right at 2×2, 2×3, 3×2 and 4×2: bottle scaled to
  the available height, no clipped or ellipsized text.
- Content: the bottle; below it the amount as `1,25 / 2 L` (capital L: in the bold widget font a
  lower-case l reads as a vertical bar; German decimal comma, at most two
  decimals, trailing zeros dropped); a streak chip `🔥 6` when streak ≥ 1; a small undo icon,
  shown only when today has an intake.
- Tap anywhere on the widget except the two controls below = `addIntake()`.
  Tap on the amount text = open the app. Tap on undo = `undoLast()`. Touch targets ≥ 48 dp
  where the size allows it.
- A widget tap gives a short vibration tick as confirmation (`VIBRATE` permission).
- The bottle is drawn with `android.graphics.Canvas` into a bitmap sized from the widget's actual
  size and shown in an `ImageView`.
- System widget corner radius and `@android:id/background` on Android 12+. Light and dark variants.
- **The level animates on change** (see "Widget animation").
- The app can ask the launcher to pin the widget (`AppWidgetManager.requestPinAppWidget`).
- A sensible `previewLayout`/preview image for the widget picker.

## Widget animation

When today's level changes (tap, undo, notification action, a change made in the app), the bottle
in the widget animates from the level it currently shows to the new one: about 500 ms, eased,
rising or falling. Reaching the goal blends to gold during the rise. Only on change; nothing
moves while idle.

- **One update, launcher-timed frames.** The app renders the frames (old level → new level) and
  sends them in a single full `updateAppWidget`: a freshly added `ViewFlipper`
  (`autoStart`, `flipInterval` 33 ms) whose child `ImageView`s carry the frames. The launcher
  plays them by itself. The last frames are identical to the final state (a hold, sharing one
  `Bitmap` instance), and a second full update inside that hold removes the flipper and shows
  the static final image. Removing the flipper from the layout is what stops it; hiding it does not.
- **Never a wrong end state.** The closing update re-reads the real state and is authoritative.
  It is sent even if the animation is cancelled. Rapid taps retarget: the next animation starts
  from the level visible at that moment, and only the newest animation may send its closing update.
- **Bitmap budget.** All frames of one update must stay well under the RemoteViews bitmap cap
  (6 × display width × height bytes); frame size and count are derived from the real display
  size. If the cap is hit anyway, fall back to a static update at once.
- Size changes, day rollover, colour or goal changes and theme switches update statically.
- The static image keeps the light/dark pair; animation frames use the current mode only.
- Fallback, only if device measurement shows the flipper is not smooth enough: push the same
  frames as separate full updates, picked by elapsed time (late frames dropped, not delayed).

## Bottle (shared visual)

One `BottleRenderer.draw(canvas, width, height, fillFraction, wavePhase, waterColor, goalReached, dark)` in
`app.pegelstand.ui.bottle`, used by the widget (bitmap) and the app (Compose `Canvas` via
`nativeCanvas`), so both show the identical bottle.

- Silhouette: rounded-shoulder bottle, short neck, cap. Outline stroke about 3 % of the width.
- Water: clipped to the inside, level = `fillFraction` clamped to 0..1, vertical gradient, top
  edge a gentle sine wave (amplitude about 2.5 % of the height, flat at 0 %). Empty part: a faint
  glass tint.
- Goal reached: cap and outline switch to the gold accent, a few small sparkles next to the bottle.
- Starting palette (one `PegelColors` object, light/dark): water gradient from the selected
  `WaterColor` (light at the top, deep at the bottom), glass tint `#E8F4FB` / `#1C2A35`, outline `#0D47A1` / `#B3E5FC`, gold `#FFB300`.

## Notification

Channel "Trink-Erinnerungen", default importance. Title `Zeit für ein Glas 💧`, text states the
deficit in glasses (`Du liegst etwa 2 Gläser hinter deinem Tagesplan.`, glasses = deficit /
mlPerTap, rounded, at least 1). Action button `+250 ml` (the real tap size) calls `addIntake()`
without opening the app. Tapping the body opens the app. One notification id, replaced, never stacked.
`POST_NOTIFICATIONS` is requested in the app, not at cold start without context.

## App (Compose, single activity)

One scrolling screen plus settings behind a gear icon.

1. **Heute**: large bottle, animated (wave moves, level eases to the new value), amount and goal,
   a large "+250 ml" button (real tap size), undo. Reaching the goal gets a visible moment
   (the bottle turns gold, a short celebration), once per day.
2. **Streak**: current streak, longest streak, average of the last 7 days.
3. **Woche**: 7 bars, Monday to Sunday, with a goal line; reached days in gold.
4. **Monat**: calendar grid, swipe or arrows between months; each day is a small drop filled in
   proportion to total / goal, gold when reached; tapping a day shows its total.
5. A card "Widget zum Startbildschirm hinzufügen" while no widget is placed.

Settings: Tagesziel, Menge pro Tap, Wasserfarbe (six swatches, the selected one marked, the
bottle on the Heute screen changes immediately), Flasche (two choices: "füllt sich" / "leert
sich"), Erinnerungen an/aus, aktiv von/bis.

Same palette as the bottle, light and dark theme, no dynamic color (the bottle is the identity).
Adaptive launcher icon: a drop or the bottle.

## Quality bar

- A claim in a report is backed by a run: the Gradle output, the test count, or a screenshot path.
- Anything that deviates from this file is listed in the report with the reason. If the spec is
  wrong or contradicts itself, stop guessing: say so in the report.
- No TODOs, stubs or placeholder screens left behind unless the brief for that phase says so.
- No suppressed lint/compiler warnings to get a build through.
