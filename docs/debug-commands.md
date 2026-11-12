# Debug commands (debug builds only)

The debug build contains `app.pegelstand/.debug.DebugReceiver`, a receiver that adb can reach
(it is guarded by the `DUMP` permission, which the adb shell has and apps cannot get). Neither the
receiver nor the settable clock exist in release builds.

Every command has the form

```sh
adb shell am broadcast -n app.pegelstand/.debug.DebugReceiver -a app.pegelstand.debug.<ACTION> [extras]
```

After every action the widgets are refreshed and the reminder alarm is rescheduled. The broadcast
result (printed by `am broadcast` as `data="…"`) is a one-line status, for example:

```
now=2026-11-12 15:00:00 (fixed) day=2026-11-12 yesterday=0 ml total=0 / 2 L (0/2000 ml) tap=250 color=BLUE mode=FILL idleWave=true streak=0/0 reminderDue=true remindersToday=0 nextWakeUp=2026-11-12 15:00:00
```

On Git Bash for Windows, prefix commands that contain a `/`-path argument with `MSYS_NO_PATHCONV=1`.
With more than one device attached, add `-s <serial>` after `adb`.

## Clock

The emulator clock cannot be set without root, so the debug build has its own: the system clock,
or a fixed "now" that stands still until it is changed or cleared. It survives app restarts.

| What | Command (after `… -a app.pegelstand.debug.`) |
|---|---|
| Freeze "now" at 15:00 today (real date) | `SET_TIME --es time 15:00` |
| Freeze at a date and time | `SET_TIME --es date 2026-11-11 --es time 03:50` |
| Move the frozen time forward | `ADVANCE_TIME --ei minutes 61` |
| Back to the system clock | `CLEAR_TIME` |

A logical day starts at 04:00, so `SET_TIME --es time 03:50` belongs to yesterday's logical day.
Every debug action refreshes the widgets afterwards, so a clock jump shows on the widget at once
(statically: a day change never animates).

## Data and settings

| What | Command |
|---|---|
| Add N taps through the repository (like the widget) | `ADD_INTAKE --ei count 3` |
| Undo the last tap | `UNDO` |
| Daily goal (1000–5000, steps of 250) | `SET_GOAL --ei ml 2000` |
| Amount per tap (50–1000, steps of 50) | `SET_ML_PER_TAP --ei ml 250` |
| Water colour | `SET_COLOR --es color TURQUOISE` (`BLUE`, `TURQUOISE`, `MINT`, `LAVENDER`, `PINK`, `CORAL`) |
| Bottle direction | `SET_BOTTLE_MODE --es mode DRAIN` (`FILL`: fills as she drinks, `DRAIN`: starts full and empties) |
| Widget idle wave on/off | `SET_IDLE_WAVE --ez enabled false` |
| Clear all data (intakes, day goals, settings, reminder bookkeeping) | `CLEAR_DATA` |
| Status line only | `STATUS` |

`CLEAR_DATA` does not touch the debug clock; use `CLEAR_TIME` for that.

### Seeding history

Writes past days directly through the DAO (the one debug exception to the single write path).
Each listed day is replaced: its intakes are removed, then one intake of the given total is
written at 12:00 of that day with the day's goal.

```sh
# Totals oldest first; the last value is yesterday. 0 leaves a day empty.
adb shell am broadcast -n app.pegelstand/.debug.DebugReceiver -a app.pegelstand.debug.SEED_HISTORY --es totals 2000,2250,1500,0,2000,2000,2500
# Same total for N days ending yesterday, with an explicit goal for those days.
adb shell am broadcast -n app.pegelstand/.debug.DebugReceiver -a app.pegelstand.debug.SEED_HISTORY --ei days 30 --ei ml 2000 --ei goal 2000
```

"Yesterday" is relative to the app's clock (fixed or system).

## Reminders

| What | Command |
|---|---|
| Run the alarm's work now (refresh widgets, remind if due, schedule next) | `RUN_CHECK` |

Notification permission for testing: `adb shell pm grant app.pegelstand android.permission.POST_NOTIFICATIONS`.
Pending alarm: `adb shell dumpsys alarm | grep -A4 app.pegelstand` (it is on the elapsed-realtime
clock; the status line's `nextWakeUp` is the wall-clock instant it stands for).

Example: behind plan at 15:00 → reminder.

```sh
R="adb shell am broadcast -n app.pegelstand/.debug.DebugReceiver -a app.pegelstand.debug"
$R.CLEAR_DATA
$R.SET_TIME --es time 15:00
$R.RUN_CHECK          # notification "… etwa 5 Gläser …"
```

## Bottle render

Renders one bottle into the app's files directory, for review or for the widget picker preview:

```sh
adb shell am broadcast -n app.pegelstand/.debug.DebugReceiver -a app.pegelstand.debug.RENDER_BOTTLE --ef fill 0.62 --ez dark false --ez goal false --es color BLUE --ei width 390 --ei height 500
adb exec-out run-as app.pegelstand cat files/bottle.png > bottle.png
```
