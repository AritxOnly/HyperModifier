package com.aritxonly.myhypermodifier;

/** Scheduling and format rules independent of the SystemUI implementation. */
final class StatusBarClockSecondsPolicy {
    private StatusBarClockSecondsPolicy() {}

    static boolean shouldTick(boolean enabled, boolean screenOn, boolean attached,
                              boolean shown, boolean demo) {
        return enabled && screenOn && attached && shown && !demo;
    }

    static long delayToNextSecond(long wallTimeMillis) {
        return 1000L - Math.floorMod(wallTimeMillis, 1000L);
    }

    static String pattern(boolean is24, boolean showAmPm) {
        return is24 ? "HH:mm:ss" : showAmPm ? "h:mm:ss a" : "h:mm:ss";
    }
}
