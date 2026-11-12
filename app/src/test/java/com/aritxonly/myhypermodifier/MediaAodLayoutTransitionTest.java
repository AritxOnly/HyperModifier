package com.aritxonly.myhypermodifier;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ContextWrapper;
import android.transition.ChangeBounds;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaAodLayoutTransitionTest {
    @Test public void coverAndContainerInterpolateInBothDirectionsWithoutFadingTheWholePlayer() {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        FrameLayout root = new FrameLayout(context);
        View background = add(root, resources, "media_bg");
        View cover = add(root, resources, "album_art");
        View title = add(root, resources, "header_title");
        View progress = add(root, resources, "media_progress_bar");
        View unrelated = add(root, resources, "unrelated");
        root.layout(0, 0, 400, 132);
        TransitionSet transition = MediaAodLayoutTransition.create(root);
        assertNotNull(transition);
        assertEquals(200, transition.getDuration());
        assertEquals(2, transition.getTransitionCount()); // No image means no wildcard image transition.
        ChangeBounds bounds = (ChangeBounds) transition.getTransitionAt(0);
        assertTrue(bounds.getTargets().contains(cover));
        assertTrue(bounds.getTargets().contains(title));
        assertTrue(bounds.getTargets().contains(root));
        assertFalse(bounds.getTargets().contains(unrelated));
        assertTrue(transition.getTransitionAt(1).getTargets().contains(progress));
        assertFalse(transition.getTransitionAt(1).getTargets().contains(root));
        int lookups = resources.lookups;
        MediaAodLayoutTransition.create(root);
        assertEquals(lookups, resources.lookups);
        for (boolean entering : new boolean[]{true, false}) {
            int fromCover = entering ? 108 : 53, toCover = entering ? 53 : 108;
            int fromHeight = entering ? 132 : 80, toHeight = entering ? 80 : 132;
            animateBounds(root, bounds, cover, fromCover, toCover);
            animateBounds(root, bounds, background, fromHeight, toHeight);
        }
        assertNull(MediaAodLayoutTransition.create(new FrameLayout(context)));
    }

    private static void animateBounds(FrameLayout root, ChangeBounds transition, View view, int from, int to) {
        view.layout(12, 12, 12 + from, 12 + from);
        TransitionValues start = new TransitionValues(view);
        transition.captureStartValues(start);
        view.layout(12, 12, 12 + to, 12 + to);
        TransitionValues end = new TransitionValues(view);
        transition.captureEndValues(end);
        Animator animator = transition.createAnimator(root, start, end);
        assertNotNull("Bounds changes must produce an animator", animator);
        animator.setDuration(200);
        animator.start();
        if (animator instanceof ValueAnimator value) value.setCurrentPlayTime(100);
        else ((AnimatorSet) animator).setCurrentPlayTime(100);
        assertTrue("Middle frame must differ from both endpoints", view.getHeight() > Math.min(from, to)
                && view.getHeight() < Math.max(from, to));
        animator.end();
        assertEquals(to, view.getHeight());
    }

    private static View add(FrameLayout root, MediaConstraintPlanTest.TestResources resources, String name) {
        View view = new View(root.getContext());
        view.setId(resources.getIdentifier(name, "id", root.getContext().getPackageName()));
        root.addView(view, new FrameLayout.LayoutParams(100, 100));
        return view;
    }
}
