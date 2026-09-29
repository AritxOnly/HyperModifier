package com.aritxonly.myhypermodifier;

/** Keeps the original expanded content height while reserving space below it. */
final class SuperIslandContentMargin {
    private SuperIslandContentMargin() {}

    static int contentHeight(int requested, int minimum, int maximum, boolean bypassMaximum) {
        int bounded = bypassMaximum ? requested : Math.min(requested, maximum);
        return Math.max(minimum, bounded);
    }

    static int hostHeight(int contentHeight, int bottomMargin) {
        return contentHeight + Math.max(0, bottomMargin);
    }
}
