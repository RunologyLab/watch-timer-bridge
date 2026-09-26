# Watch Timer Bridge v1.0.6

**Watch Timer Bridge enables compatible third-party Wear OS watch faces to display the live numeric countdown from Samsung Timer in a dedicated `SHORT_TEXT` complication slot.** It is a bridge, not another timer app; Samsung Timer remains the actual timer and source of truth.

Samsung Timer can show a running timer in Samsung's Now Bar, but the Now Bar is a shared display for ongoing activities. Another activity may take priority and displace the timer. Samsung Timer's live numeric countdown is generally not freely selectable as a standard complication on third-party faces. This bridge reads Samsung Timer's active notification `Chronometer`, derives the countdown, and publishes it through Wear OS's complication system:

**Samsung Timer → notification Chronometer → Watch Timer Bridge → Wear OS `SHORT_TEXT` complication → third-party watch face**

**Tested setup:** Samsung Galaxy Watch 8 Classic, Samsung Timer, Facer, and the Facer Pip-Boy SE watch face. [See the photo and setup details in the README](https://github.com/RunologyLab/watch-timer-bridge/blob/main/README.md). Other third-party watch faces with compatible `SHORT_TEXT` slots may work, but broader compatibility has not yet been verified.

### v1.0.6 features

- Live Samsung Timer countdown exposed as a `SHORT_TEXT` complication, with a prompt update after the timer starts or stops.
- `H:MM:SS`, including seconds for timers longer than one hour while the screen is interactive, where the watch face has enough space.
- Default empirical −1-second display adjustment for closer visual synchronization; adjustable on the watch without changing Samsung Timer's alarm.
- Stopwatch glyph when no timer is active.
- Reduced redundant updates and no per-second app updates while the screen is off.
- Same package ID and signing certificate as v1.0.5-test, allowing an update that preserves saved settings and notification access.

Install the APK **on the watch**, grant notification access, and select **Watch Timer Countdown** in a compatible `SHORT_TEXT` complication slot. [Full installation steps and the optional ADB command](https://github.com/RunologyLab/watch-timer-bridge/blob/main/README.md#install-on-the-watch) are in the README. The separate watch face is not included.

**Privacy and limits:** Android grants notification listener access at the system level. This app processes only Samsung Timer notifications for the countdown, stores no notification text, requests no Internet permission, and transmits no watch data. It relies on Samsung Timer's notification layout rather than an official complication API, so a future Samsung update could require a bridge update. See [PRIVACY.md](https://github.com/RunologyLab/watch-timer-bridge/blob/main/PRIVACY.md).

This is an independent project and is not affiliated with or endorsed by Samsung, Google, Facer, or the creators of the Pip-Boy SE watch face.
