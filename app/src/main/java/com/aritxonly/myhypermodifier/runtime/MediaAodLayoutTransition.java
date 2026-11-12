package com.aritxonly.myhypermodifier;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.transition.ChangeBounds;
import android.transition.ChangeImageTransform;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import java.util.Map;
import java.util.WeakHashMap;

/** Short, targeted content morph at the native AOD boundary, never at animation completion. */
final class MediaAodLayoutTransition {
    private static final long DURATION_MS = 200;
    private static final PathInterpolator EASE = new PathInterpolator(.2f, 0f, 0f, 1f);
    private static final Map<Resources, Targets> TARGETS = new WeakHashMap<>();
    private static final String[] BOUNDS = {
            "media_bg", "album_art", "album_art_image", "header_title", "header_artist"};
    private static final String[] FADE = {
            "action0", "action1", "action2", "action3", "action4", "media_seamless",
            "media_progress_bar", "media_elapsed_time", "media_total_time"};

    static boolean begin(Object controller) {
        if (ModuleSettings.disableMediaIslandHooks
                || !"large_cover".equals(ModuleSettings.activeMediaPreset(false))
                || !ValueAnimator.areAnimatorsEnabled()) return false;
        View player = MediaPresetViewProperties.normalPlayer(controller);
        if (!(player instanceof ViewGroup root) || !root.isAttachedToWindow()
                || !root.isLaidOut() || !root.isShown()) return false;
        TransitionSet transition = create(root);
        if (transition == null) return false;
        // Capture before styles, constraints, visibility and native background size change.
        // The existing early-entry hook starts this before the native 300 ms material delay.
        // No extra completion replay, delayed work, snapshot or frame hook is installed.
        TransitionManager.beginDelayedTransition(root, transition);
        return true;
    }

    static TransitionSet create(ViewGroup root) {
        Resources resources = root.getResources();
        String pkg = root.getContext().getPackageName();
        Targets targets;
        synchronized (TARGETS) {
            targets = TARGETS.get(resources);
            if (targets == null || !targets.pkg.equals(pkg)) {
                targets = new Targets(pkg, ids(resources, pkg, BOUNDS), ids(resources, pkg, FADE));
                TARGETS.put(resources, targets);
            }
        }
        if (targets.bounds[0] == 0 || root.findViewById(targets.bounds[0]) == null) return null;
        ChangeBounds bounds = new ChangeBounds();
        // The player is wrap_content and clips children on the baseline ROM. Its bounds
        // must shrink with the background, otherwise the first AOD measure clips the cover.
        bounds.addTarget(root);
        ChangeImageTransform images = new ChangeImageTransform();
        Fade fade = new Fade();
        for (int id : targets.bounds) {
            View view = id == 0 ? null : root.findViewById(id);
            if (view != null) bounds.addTarget(view);
        }
        View image = targets.bounds[2] == 0 ? null : root.findViewById(targets.bounds[2]);
        if (image != null) images.addTarget(image);
        for (int id : targets.fade) {
            View view = id == 0 ? null : root.findViewById(id);
            if (view != null) fade.addTarget(view);
        }
        TransitionSet result = new TransitionSet().setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(bounds);
        // Empty targets mean 'all views' to Android transitions. Never animate unrelated views.
        if (image != null) result.addTransition(images);
        if (!fade.getTargets().isEmpty()) result.addTransition(fade);
        result.setDuration(DURATION_MS).setInterpolator(EASE);
        return result;
    }

    private static int[] ids(Resources resources, String pkg, String[] names) {
        int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) ids[i] = resources.getIdentifier(names[i], "id", pkg);
        return ids;
    }
    private record Targets(String pkg, int[] bounds, int[] fade) {}
}
