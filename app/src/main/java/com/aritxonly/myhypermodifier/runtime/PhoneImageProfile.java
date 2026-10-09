package com.aritxonly.myhypermodifier;

import org.json.JSONObject;

public final class PhoneImageProfile {
    private PhoneImageProfile() {}
    public static float[] read(String json, String key) {
        try {
            // The new Xiaomi 17 cutout has a fixed calibration, including on upgrades
            // from builds that saved temporary per-image tuning.
            JSONObject profile = "xiaomi-17".equals(key)
                    ? new JSONObject(PhoneImageDefaults.JSON).optJSONObject(key)
                    : new JSONObject(json).optJSONObject(key);
            if (profile == null) profile = new JSONObject(PhoneImageDefaults.JSON).optJSONObject(key);
            if (profile != null) return new float[] {
                    PhoneImageGeometry.offset((float) profile.optDouble("x", 0)),
                    PhoneImageGeometry.offset((float) profile.optDouble("y", 0)),
                    PhoneImageGeometry.scale((float) profile.optDouble("scale", 1)) };
        } catch (Exception ignored) {}
        return new float[] { 0, 0, 1 };
    }
}
