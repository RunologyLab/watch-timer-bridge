# Watch Timer Bridge

An independent Wear OS `SHORT_TEXT` complication data source for the built-in
Samsung Timer. When a timer is running, compatible watch faces show a live
countdown; when it stops, this provider sends a stopwatch glyph as the idle text. Tapping
the complication tries to open Samsung Timer. This is an independent project,
not affiliated with Samsung or Google.

## Compatibility and limitations

- Tested with Samsung Timer on a Galaxy Watch 8 Classic. Starting a timer
  updates the tested watch face immediately; the default -1-second display
  correction closely matched the Now Bar. A two-hour timer displayed seconds
  on that watch. Other Galaxy Watch models and faces need testing.
- Requires a Wear OS watch with the Samsung Timer package
  `com.samsung.android.watch.timer` and a watch face offering a `SHORT_TEXT`
  complication slot. The watch face is not included.
- Reads Samsung Timer's `customDisplayBundle/cardChronometerRemoteView`
  `Chronometer` from its active notification. Samsung can change this private
  notification structure in future updates. Other timer apps are unsupported.
- A notification access grant is required. Android can grant broad access to
  notifications, although this app handles **only Samsung Timer** and requests
  no Internet permission. See [PRIVACY.md](PRIVACY.md).

## Install from GitHub Releases

1. Download `watch-timer-bridge-v1.0.6.apk` from the GitHub Release.
2. Install the APK **on the watch**, not the phone. When using wireless ADB or
   a phone installer such as GeminiMan, temporarily enable the watch's developer
   options and wireless debugging, then connect and install the APK.
3. Launch **Watch Timer Bridge** on the watch and tap **Open notification
   access**. The app opens a system settings page where you can grant access;
   Android does not provide a regular permission popup for notification
   listeners. If your watch has no compatible settings page, try the watch's
   **Settings > Apps > Special access > Notification access**. The menu layout
   depends on the watch. You can also use the ADB command below during setup.
4. Edit a `SHORT_TEXT` complication slot in your chosen watch face and select
  **Watch Timer Countdown**. Start Samsung Timer and return to the face to
  verify that numbers appear without locking and waking the screen.
5. After installation you can switch off wireless debugging. Keep notification
   access enabled for the countdown to work.

If neither settings shortcut opens on your watch, the ADB connection used to
install the APK can enable the listener. In GeminiMan WearOS Manager's
**Send Commands** field (already connected to the watch), run:

```sh
cmd notification allow_listener com.watchtimerbridge.wear/com.watchtimerbridge.wear.TimerListener
```

Or use the same command prefixed with `adb shell` from a computer connected to
the watch. **This grants notification access; run it only if you want this app
to read watch notifications.** Open the app again and tap **Refresh status**.
The ADB step is optional if the watch's settings page works.

The package ID is `com.watchtimerbridge.wear`. Version 1.0.6 uses the same
signing certificate as the 1.0.5-test build and can update it without losing
saved correction or notification access. Earlier private bridge builds
used a different package ID and will remain installed until you remove them.
If you are moving from one of those builds, select the new data source in the
watch face. The watch face itself is a separate app and is not distributed here.

## Build from source

Requires a Java runtime with the `jdk.compiler` module, Android SDK platform
`android.jar` (API 23 or higher), `aapt`, `dalvik-exchange`, `zipalign`, `zip`,
and `apksigner`. Set `ANDROID_JAR` if it is not at the script's default path.
Create and retain a private PKCS12 signing key. Keep its password in a file
outside this repository. For example:

```sh
SIGNING_KEYSTORE=/private/path/release-key.p12 \
SIGNING_PASSWORD_FILE=/private/path/signing-password.txt \
  bash build.sh
```

**Do not publish signing keys, their passwords, or build logs containing
credentials.** The same private key is required to sign future updates with
this package ID. The source archive contains neither a private key nor any
third-party artwork. The generated APK is a GitHub Release asset and
should not be added to Git history.

The source currently sends Wear OS complication data using a minimal wire
implementation verified by earlier on-watch builds. Keep this compatibility
layer under review when updating Wear OS or making wider compatibility claims.

## Countdown precision

Wear OS's built-in stopwatch complication text switches to `H:MM` when at
least one hour remains, hiding seconds. While the watch is interactive and a
long timer is active, this version sends full `H:MM:SS` text once per second
(for example, `2:00:00` or `1:58:59`). Below one hour it uses the system's
self-updating `MM:SS` text. When the screen is off, it uses the system text
without per-second app updates to conserve battery. Other watch faces may
apply their own font or width constraints; test long timers on the watch.

The native Android Chronometer truncates countdown seconds, whereas the Wear OS
time-difference renderer rounds them up. Version 1.0.6 starts with a -1 second
deadline correction based on a Samsung Watch comparison with its Now Bar.
Tap **Countdown correction** in the on-watch app to cycle through -2, -1, 0,
and -3 seconds if your watch shows a different offset; the setting is saved
on the watch and survives app updates. Compare against Samsung Timer's
displayed countdown while both screens are active. This value is an empirical
display adjustment, not a change to the actual Samsung Timer alarm.

On some Watch Face Format faces, an expression that conditionally replaces
the idle text with a drawn icon can remain on that icon until screen wake.
This provider now supplies a stopwatch text glyph (`⏱︎`) when idle, so a face
using a single `SHORT_TEXT` part can render the glyph and the countdown in
one continuously updating element. Custom watch faces using a conditional
icon should render `COMPLICATION.TEXT` directly for reliable transitions.

The app displays a local status summary on the watch. It logs only
notification arrival times, whether a timer deadline was found, active slot
count, and whether the system accepted a complication update; it does not
record titles or text from notifications. You can press **Send face update
now** and then return to the face to help diagnose delayed updates.

## Source license

The source code in this repository is offered under the MIT License. No
third-party artwork, logos, fonts, or animation assets are included.
