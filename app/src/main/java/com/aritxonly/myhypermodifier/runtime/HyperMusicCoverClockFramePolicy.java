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
}
