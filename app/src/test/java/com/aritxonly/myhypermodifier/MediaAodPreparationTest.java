package com.aritxonly.myhypermodifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class MediaAodPreparationTest {
    enum AnimationType { FULL_AOD, NORMAL }
    public static final class Controller {
        public static boolean mEnableFullAod;
        public final List<String> events = new ArrayList<>();
        public Runnable applyStyle;
        public void updateMediaNotificationStyle() {
            events.add("small-cover-and-hidden-actions");
            if (applyStyle != null) applyStyle.run();
            ModuleSettings.inFullAod = true; // The existing native callback hook sets the state.
        }
    }
    public static final class Panel { public Controller notifiFullAodController = new Controller(); }
    @Test public void preparesAodBeforeTheNativeParentAnimationAndDoesNotReplayAtCompletion() {
        Panel panel = new Panel();
        String old = ModuleSettings.mediaLayoutPreset;
        boolean oldAod = ModuleSettings.inFullAod, oldDisabled = ModuleSettings.disableMediaIslandHooks;
        try {
            ModuleSettings.mediaLayoutPreset = "large_cover"; ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.inFullAod = false; Controller.mEnableFullAod = true;
            assertTrue(MediaAodPreparation.beforeWakeAnimation(panel, false, AnimationType.FULL_AOD));
            panel.notifiFullAodController.events.add("native-parent-shrink-starts");
            assertEquals(List.of("small-cover-and-hidden-actions", "native-parent-shrink-starts"), panel.notifiFullAodController.events);
            assertFalse(MediaAodPreparation.beforeWakeAnimation(panel, false, AnimationType.FULL_AOD));
            assertEquals(2, panel.notifiFullAodController.events.size());
        } finally {
            ModuleSettings.mediaLayoutPreset = old; ModuleSettings.inFullAod = oldAod; ModuleSettings.disableMediaIslandHooks = oldDisabled;
        }
    }
    @Test public void unrelatedWakePathsSystemPresetAndOptOutDoNotPrepareMedia() {
        Panel panel = new Panel();
        String old = ModuleSettings.mediaLayoutPreset;
        boolean oldAod = ModuleSettings.inFullAod, oldDisabled = ModuleSettings.disableMediaIslandHooks;
        try {
            ModuleSettings.mediaLayoutPreset = "large_cover"; ModuleSettings.inFullAod = false;
            ModuleSettings.disableMediaIslandHooks = false; Controller.mEnableFullAod = true;
            assertFalse(MediaAodPreparation.beforeWakeAnimation(panel, true, AnimationType.FULL_AOD));
            assertFalse(MediaAodPreparation.beforeWakeAnimation(panel, false, AnimationType.NORMAL));
            Controller.mEnableFullAod = false;
            assertFalse(MediaAodPreparation.beforeWakeAnimation(panel, false, AnimationType.FULL_AOD));
            Controller.mEnableFullAod = true; ModuleSettings.mediaLayoutPreset = "system";
            assertFalse(MediaAodPreparation.beforeWakeAnimation(panel, false, AnimationType.FULL_AOD));
            ModuleSettings.mediaLayoutPreset = "large_cover"; ModuleSettings.disableMediaIslandHooks = true;
            assertFalse(MediaAodPreparation.beforeWakeAnimation(panel, false, AnimationType.FULL_AOD));
            assertTrue(panel.notifiFullAodController.events.isEmpty());
        } finally {
            ModuleSettings.mediaLayoutPreset = old; ModuleSettings.inFullAod = oldAod; ModuleSettings.disableMediaIslandHooks = oldDisabled;
        }
    }
}
