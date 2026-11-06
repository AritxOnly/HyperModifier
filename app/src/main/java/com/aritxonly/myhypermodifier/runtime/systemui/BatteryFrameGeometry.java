package com.aritxonly.myhypermodifier;

import android.graphics.Matrix;
import android.graphics.Path;
import androidx.core.graphics.PathParser;

/** Packaged SystemUI geometry from the 4.0.4.0 base: digit-style body with the original system marks
 * centered inside. Theme overlays are intentionally not consulted.
 * Parsed lazily once when first enabled. No bitmap, offscreen layer or per-frame path operations.
 */
final class BatteryFrameGeometry {
    private BatteryFrameGeometry() {}
    private static final String BODY = "M18.327 3.88C20.402 3.88 21.439 3.88 22.232 4.284 22.929 4.639 23.496 5.206 23.851 5.903 24.048 6.29 24.148 6.735 24.2 7.331 24.219 7.56 24.229 7.674 24.28 7.748 24.3 7.776 24.313 7.79 24.339 7.812 24.408 7.87 24.542 7.895 24.811 7.944 24.9 7.96 24.975 7.983 25.043 8.018 25.233 8.115 25.388 8.27 25.485 8.459 25.595 8.675 25.594 8.958 25.594 9.522V10.478C25.594 11.042 25.594 11.325 25.485 11.54 25.388 11.73 25.233 11.885 25.043 11.982 24.975 12.017 24.9 12.04 24.811 12.056 24.542 12.105 24.408 12.13 24.339 12.188 24.313 12.21 24.3 12.224 24.28 12.252 24.229 12.326 24.219 12.44 24.2 12.669 24.148 13.265 24.048 13.71 23.851 14.097 23.496 14.794 22.929 15.361 22.232 15.716 21.439 16.12 20.402 16.12 18.327 16.12H6.453C6.453 16.12 5.415 16.12 4.622 15.716 3.925 15.361 3.359 14.794 3.003 14.097 2.6 13.305 2.6 12.267 2.6 10.192V9.808C2.6 7.733 2.6 6.695 3.003 5.903 3.359 5.206 3.925 4.639 4.622 4.284 5.415 3.88 6.453 3.88 8.528 3.88H18.327Z";
    private static final String BOLT = "M15.142,4.111C15.265,4.111 15.354,4.229 15.321,4.347L14.003,9.066L15.74,9.066C16.087,9.066 16.26,9.066 16.351,9.137C16.431,9.199 16.478,9.294 16.48,9.395C16.482,9.511 16.378,9.649 16.171,9.926L11.749,15.821C11.717,15.864 11.667,15.889 11.614,15.889C11.503,15.889 11.422,15.782 11.452,15.675L12.785,10.935H10.762C10.406,10.935 10.227,10.935 10.136,10.862C10.056,10.798 10.009,10.702 10.009,10.6C10.009,10.482 10.119,10.342 10.34,10.063L14.996,4.182C15.031,4.137 15.085,4.111 15.142,4.111Z";
    private static final String QUICK = "M14.814,4.855C15.02,4.308 15.124,4.035 15.065,3.953C15.034,3.909 14.986,3.881 14.932,3.875C14.832,3.864 14.644,4.088 14.269,4.536L10.635,8.868C10.39,9.16 10.268,9.306 10.28,9.431C10.286,9.498 10.316,9.562 10.364,9.611C10.451,9.7 10.642,9.7 11.023,9.7L12.51,9.7L10.794,12.205C10.6,12.488 10.503,12.63 10.522,12.747C10.532,12.812 10.564,12.871 10.611,12.916C10.697,12.998 10.869,12.998 11.212,12.998H12.464L11.654,15.147C11.448,15.693 11.345,15.966 11.403,16.048C11.435,16.092 11.483,16.12 11.536,16.126C11.636,16.137 11.824,15.913 12.199,15.466L15.832,11.131C16.076,10.839 16.199,10.693 16.187,10.569C16.18,10.501 16.15,10.437 16.103,10.389C16.015,10.3 15.825,10.3 15.444,10.3L13.962,10.3L15.674,7.794C15.867,7.511 15.964,7.369 15.945,7.252C15.934,7.188 15.903,7.128 15.856,7.083C15.77,7.002 15.598,7.002 15.256,7.002H14.002L14.814,4.855Z";
    private static final String PERFORMANCE = "M4.528,3.599C4.546,3.655 4.546,3.716 4.546,3.838V4.668C4.546,4.845 4.546,4.933 4.509,4.983C4.476,5.027 4.427,5.054 4.373,5.059C4.311,5.064 4.236,5.017 4.085,4.924L2.67,4.046L1.255,4.924C1.105,5.017 1.03,5.064 0.968,5.059C0.914,5.054 0.864,5.027 0.832,4.983C0.795,4.933 0.795,4.845 0.795,4.668V3.838C0.795,3.716 0.795,3.655 0.813,3.599C0.828,3.551 0.853,3.505 0.886,3.467C0.924,3.423 0.976,3.391 1.08,3.326L2.353,2.537C2.468,2.465 2.526,2.429 2.587,2.415C2.642,2.403 2.699,2.403 2.753,2.415C2.815,2.429 2.873,2.465 2.988,2.537L4.261,3.326C4.365,3.391 4.417,3.423 4.454,3.467C4.488,3.505 4.513,3.551 4.528,3.599Z M4.528,6.336C4.545,6.391 4.545,6.452 4.545,6.574V7.404C4.545,7.581 4.545,7.669 4.508,7.719C4.476,7.763 4.427,7.79 4.373,7.795C4.311,7.8 4.236,7.753 4.085,7.66L2.67,6.782L1.255,7.66C1.105,7.753 1.03,7.8 0.968,7.795C0.914,7.79 0.864,7.763 0.832,7.719C0.795,7.669 0.795,7.581 0.795,7.404V6.574C0.795,6.452 0.795,6.391 0.812,6.336C0.828,6.287 0.853,6.242 0.886,6.203C0.924,6.159 0.976,6.127 1.08,6.062L2.353,5.273C2.468,5.202 2.526,5.166 2.587,5.152C2.642,5.14 2.699,5.14 2.753,5.152C2.815,5.166 2.873,5.202 2.988,5.273L4.26,6.062C4.365,6.127 4.417,6.159 4.454,6.203C4.488,6.242 4.513,6.287 4.528,6.336Z";

    private static final class Shapes {
        static final Path BODY_PATH = PathParser.createPathFromPathData(BODY);
        static final Path[] MARKS = createMarks();
    }

    static Path body() {
        return Shapes.BODY_PATH;
    }

    static Path mark(boolean charging, boolean quick, boolean performance) {
        return Shapes.MARKS[StatusBarBatteryFramePolicy.shapeIndex(charging, quick, performance)];
    }

    private static Path[] createMarks() {
        Path[] shapes = new Path[6];
        for (int i = 1; i < shapes.length; i++) {
            Path marks = new Path();
            boolean performance = i >= 3;
            int charge = i % 3;
            if (charge != 0) {
                Path mark = PathParser.createPathFromPathData(charge == 2 ? QUICK : BOLT);
                Matrix placement = new Matrix();
                // Let the original lightning extend slightly beyond the solid body's top/bottom.
                // Combined performance states keep the same height and shift only horizontally.
                float scale = charge == 2 ? 1.05f : 1.10f;
                placement.setScale(scale, scale, 13.25f, 10f);
                if (performance) placement.postTranslate(3f, 0f);
                mark.transform(placement);
                marks.addPath(mark);
            }
            if (performance) {
                Path mark = PathParser.createPathFromPathData(PERFORMANCE);
                mark.offset(charge == 0 ? 10.5f : 6.5f, 4.9f);
                marks.addPath(mark);
            }
            shapes[i] = marks;
        }
        return shapes;
    }
}
