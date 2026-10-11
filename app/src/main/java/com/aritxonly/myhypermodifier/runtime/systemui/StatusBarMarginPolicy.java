package com.aritxonly.myhypermodifier;

/** Preserves native padding updates and prevents settings refreshes from accumulating offsets. */
final class StatusBarMarginPolicy {
    private int baseLeft;
    private int baseRight;
    private int appliedLeft;
    private int appliedRight;

    StatusBarMarginPolicy(int left, int right) {
        baseLeft = appliedLeft = left;
        baseRight = appliedRight = right;
    }

    int[] resolve(int currentLeft, int currentRight, float leftDp, float rightDp, float density) {
        if (currentLeft != appliedLeft) baseLeft = currentLeft;
        if (currentRight != appliedRight) baseRight = currentRight;
        appliedLeft = baseLeft + Math.round(bounded(leftDp) * density);
        appliedRight = baseRight + Math.round(bounded(rightDp) * density);
        return new int[]{appliedLeft, appliedRight};
    }

    private static float bounded(float dp) {
        return Float.isFinite(dp) ? Math.max(-2f, Math.min(2f, dp)) : 0f;
    }
}
