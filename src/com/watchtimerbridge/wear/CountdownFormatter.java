package com.watchtimerbridge.wear;

import java.util.Locale;

/** Bright-screen display for timers too long for Wear OS's seconds precision. */
final class CountdownFormatter {
    private CountdownFormatter() { }

    static boolean needsFullText(long remainingMillis) {
        return remainingMillis > 0 && (remainingMillis + 999L) / 1000L >= 3600L;
    }

    static String format(long remainingMillis) {
        long seconds = Math.max(0L, (remainingMillis + 999L) / 1000L);
        long hours = seconds / 3600L;
        return String.format(Locale.US, "%d:%02d:%02d", hours,
                (seconds / 60L) % 60L, seconds % 60L);
    }
}
