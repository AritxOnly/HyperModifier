package com.aritxonly.myhypermodifier;

/** Raw input is unknown when adopting late: never pretend an effective output is that input. */
final class NotificationBottomState {
    float input = Float.NaN;
    private float nativeOutput = Float.NaN, applied = Float.NaN;
    boolean overridden;
    void nativeResult(float input, float output) {
        this.input = input;
        nativeOutput = output;
        applied = output;
        overridden = false;
    }
    void adopt(float output) { nativeResult(Float.NaN, output); }
    float baseline(float current) {
        // Respect a later write from another module rather than restoring over its value.
        if (!overridden || Float.compare(current, applied) != 0) nativeOutput = current;
        return nativeOutput;
    }
    void applied(float baseline, float result) {
        nativeOutput = baseline;
        applied = result;
        overridden = Float.compare(baseline, result) != 0;
    }
}
