package com.watchtimerbridge.wear;

/** Verify the two-hour bug and the transition back to Wear's stopwatch text. */
public final class CountdownFormatterTest {
    private static void check(long millis, boolean fullText, String expected) {
        if (CountdownFormatter.needsFullText(millis) != fullText)
            throw new AssertionError("wrong display mode at " + millis);
        if (!CountdownFormatter.format(millis).equals(expected))
            throw new AssertionError(millis + " -> " + CountdownFormatter.format(millis));
    }

    public static void main(String[] args) {
        check(7200000L, true, "2:00:00");
        check(7139000L, true, "1:58:59");
        check(3600000L, true, "1:00:00");
        check(3599000L, false, "0:59:59");
        check(61000L, false, "0:01:01");
        check(0L, false, "0:00:00");
    }
}
