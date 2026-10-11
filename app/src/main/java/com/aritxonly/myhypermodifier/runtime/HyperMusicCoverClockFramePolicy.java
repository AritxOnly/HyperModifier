package com.aritxonly.myhypermodifier;

final class HyperMusicCoverClockFramePolicy {
    private HyperMusicCoverClockFramePolicy() { }
    static final class Gate {
        private long frame = Long.MIN_VALUE;
        void abort() { frame = Long.MIN_VALUE; }
        boolean begin(long currentFrame) {
            if (frame == currentFrame) return false;
            frame = currentFrame; return true;
        }
    }
    static final class ExitPose {
        final float[] held, nativeValues;
        ExitPose(float[] held, float[] nativeValues) {
            this.held = held.clone(); this.nativeValues = nativeValues.clone();
        }
        float request(int component, float value, boolean visible) {
            nativeValues[component] = value;
            return visible ? held[component] : value;
        }
    }
    static boolean retainExit(boolean captured, boolean leaving, boolean shown, float alpha) {
        return captured && leaving && shown && Float.isFinite(alpha) && alpha > 0f;
    }
}
