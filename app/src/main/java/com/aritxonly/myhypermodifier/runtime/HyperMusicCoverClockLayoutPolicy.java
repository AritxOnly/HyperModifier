package com.aritxonly.myhypermodifier;

final class HyperMusicCoverClockLayoutPolicy {
    private HyperMusicCoverClockLayoutPolicy() { }
    static float[] positions(float left, float width, float hour, float colon, float minute, boolean twoRows) {
        float start = left + (width - hour - colon - minute) / 2f;
        return twoRows ? new float[]{left + (width - hour) / 2f,
                left + (width - minute) / 2f, start + hour}
                : new float[]{start, start + hour, start + hour};
    }
}
