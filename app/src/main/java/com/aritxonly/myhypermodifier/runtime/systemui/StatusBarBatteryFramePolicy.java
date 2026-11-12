package com.aritxonly.myhypermodifier;

final class StatusBarBatteryFramePolicy {
    private StatusBarBatteryFramePolicy() {}

    static float maskShift(int level, boolean start) {
        int bounded = Math.max(0, Math.min(100, level));
        float shift = -23f * (1f - bounded / 100f);
        if (bounded == 0) shift *= 2f;
        return start ? shift : -shift;
    }

    static int shapeIndex(boolean charging, boolean quick, boolean performance) {
        return (quick ? 2 : charging ? 1 : 0) + (performance ? 3 : 0);
    }
}
