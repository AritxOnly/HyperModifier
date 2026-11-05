package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;
import static org.junit.Assert.*;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class FoldVolumeRadiusTest {
    private final float original = ModuleSettings.volumePanelRadius;
    private final boolean enabled = ModuleSettings.moduleHooksEnabled;
    private final boolean compatibility = ModuleSettings.systemUiCompatibilityMode;

    @After public void restore() {
        ModuleSettings.volumePanelRadius = original;
        ModuleSettings.moduleHooksEnabled = enabled;
        ModuleSettings.systemUiCompatibilityMode = compatibility;
    }

    @Test public void popupBackgroundUsesCurrentDensityAndRestoresStock() {
        ShadowBuild.setDevice("lhasa");
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        ModuleSettings.volumePanelRadius = 30;
        Resources res = new Resources(org.robolectric.RuntimeEnvironment.getApplication().getAssets(), Resources.getSystem().getDisplayMetrics(), Resources.getSystem().getConfiguration()) {
            @Override public String getResourceEntryName(int id) {
                return id == 1 ? "o3_miui_volume_bg_radius" : "miui_volume_seekbar_progress_radius";
            }
        };
        assertEquals(30 * res.getDisplayMetrics().density, ResourceOverrides.replacementDimension(res, 1), 0f);
        assertNull(ResourceOverrides.replacementDimension(res, 2));
        ModuleSettings.volumePanelRadius = 0;
        assertNull(ResourceOverrides.replacementDimension(res, 1));
    }

    public static final class Column {
        int calls;
        boolean mode;
        public void setRadius(boolean value) { calls++; mode = value; }
    }

    @Test public void onlyLivePopupColumnsReplayOnChangedValues() throws Exception {
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        FoldVolumeColumns columns = new FoldVolumeColumns(Column.class.getMethod("setRadius", boolean.class));
        Column popup = new Column(), reused = new Column();
        columns.remember(popup, true, true);
        columns.remember(reused, true, true);
        columns.remember(reused, false, false);
        ModuleSettings.volumePanelRadius = 30;
        columns.onSettingsChanged();
        ModuleSettings.volumePanelRadius = 31;
        columns.onSettingsChanged();
        ShadowLooper.idleMainLooper();
        assertEquals(1, popup.calls);
        assertTrue(popup.mode);
        columns.onSettingsChanged();
        ShadowLooper.idleMainLooper();
        assertEquals(1, popup.calls);
        ModuleSettings.volumePanelRadius = 0;
        columns.onSettingsChanged();
        ShadowLooper.idleMainLooper();
        assertEquals(2, popup.calls);
        assertEquals(0, reused.calls);
    }
}
