# Watch Timer Bridge v1.0.6

An independent Samsung Timer countdown data source for Wear OS `SHORT_TEXT`
complications. Install the APK **on the watch** and grant notification access.
Choose **Watch Timer Countdown** in a compatible watch face. Setup details and
the fallback ADB command are in [README.md](README.md).

The running timer now appears immediately on the tested Galaxy Watch 8 Classic
watch face. While the screen is active, timers lasting one hour or more show
`H:MM:SS` rather than dropping seconds. The default display correction is -1
second and is adjustable on the watch. The same long-timer code was tested on
the watch as v1.0.5-test; v1.0.6 changes only release labels and version metadata.

The app uses the same package ID and signing certificate as v1.0.5-test, so it
installs as an update and preserves saved settings. The separate watch face
does not need updating and is not included.

**Compatibility:** verified by one user on a Galaxy Watch 8 Classic with
Samsung Timer and a `SHORT_TEXT` face. Other models and faces are unverified.
The integration depends on Samsung Timer's notification layout, which may
change. Android's notification access covers all app notifications at the
system level; this app processes only Samsung Timer notifications and has no
Internet permission. See [PRIVACY.md](PRIVACY.md).
