# Privacy

Watch Timer Bridge runs on the watch. To show Samsung Timer's remaining time,
it requests Android notification listener access. Android may grant such a
listener access to notifications from **all installed apps**, including their
contents. The program selects only notifications whose package is
`com.samsung.android.watch.timer`; it does not copy or store notification titles,
messages, or other applications' notification content.

The app derives Samsung Timer's deadline from its local notification and gives
Wear OS the countdown data required to render the complication. It stores only
a short, on-device log of complication update status and timestamps for
diagnosing errors. It does not include advertising, analytics, an account, or
the Android Internet permission, and does not upload watch data. You may turn
off notification access in the watch settings or uninstall the app at any time.
