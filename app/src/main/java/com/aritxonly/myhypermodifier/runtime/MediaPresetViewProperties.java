package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import android.view.View;
import android.widget.ImageView;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Targeted post-apply styling; snapshots retain only scalar values and an enum. */
final class MediaPresetViewProperties {
    private static final Map<View, Original> ORIGINALS = new WeakHashMap<>();
    private static final Map<Resources, ActionIds> IDS = new WeakHashMap<>();

    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> result = new HashMap<>();
            for (Field field : type.getFields()) result.put(field.getName(), field);
            return result;
        }
    };

    static boolean enabledPreset() { return enabledPreset(false) || enabledPreset(true); }

    private static boolean selectedPreset(boolean island) {
        String preset = ModuleSettings.activeMediaPreset(island);
        return !ModuleSettings.disableMediaIslandHooks && ("compact".equals(preset) || "large_cover".equals(preset));
    }

    private static boolean enabledPreset(boolean island) {
        return selectedPreset(island) && (island || !ModuleSettings.inFullAod);
    }

    static boolean hidesNormalView(View view) {
        if (ModuleSettings.disableMediaIslandHooks) return false;
        if (ModuleSettings.inFullAod && ModuleSettings.hideAodActions) return true;
        return selectedPreset(false) && MediaConstraintPlan.hidesView(view.getContext(),
                MediaConstraintCustomizer.presetXml(ModuleSettings.activeMediaPreset(false), false), false, view.getId());
    }

    static void applyController(Object controller) {
        MediaNotificationGlow.bind(controller);
        synchronized (ORIGINALS) {
            if (!enabledPreset() && ORIGINALS.isEmpty() && !ModuleSettings.inFullAod) return;
        }
        try {
            Object holder = read(controller, "holder");
            if (holder == null) holder = read(controller, "mediaViewHolder");
            Object player = read(holder, "player");
            if (player instanceof View root) apply(root);
            Object dummy = read(controller, "dummyHolder");
            Object dummyPlayer = read(dummy, "player");
            if (dummyPlayer instanceof View root) apply(root);
        } catch (IllegalAccessException ignored) { }
    }

    private static Object read(Object object, String name) throws IllegalAccessException {
        if (object == null) return null;
        Field field = FIELDS.get(object.getClass()).get(name);
        return field == null ? null : field.get(object);
    }

    static View normalPlayer(Object controller) {
        try {
            Object player = read(read(controller, "holder"), "player");
            return player instanceof View view ? view : null;
        } catch (IllegalAccessException ignored) { return null; }
    }

    static void apply(View root) {
        MediaNotificationGlow.refresh(root);
        MediaLargeCoverStyle.apply(root);
        boolean enabled = enabledPreset();
        synchronized (ORIGINALS) {
            if (!enabled && ORIGINALS.isEmpty() && !ModuleSettings.inFullAod) return;
            Resources resources = root.getResources();
            ActionIds ids;
            synchronized (IDS) {
                ids = IDS.get(resources);
                String packageName = root.getContext().getPackageName();
                if (ids == null || !ids.packageName.equals(packageName)) {
                    ids = new ActionIds(packageName, new int[]{
                            resources.getIdentifier("action0", "id", packageName),
                            resources.getIdentifier("action1", "id", packageName),
                            resources.getIdentifier("action2", "id", packageName),
                            resources.getIdentifier("action3", "id", packageName),
                            resources.getIdentifier("action4", "id", packageName)},
                            resources.getIdentifier("media_bg", "id", packageName),
                            resources.getIdentifier("media_bg_view", "id", packageName),
                            new int[]{resources.getIdentifier("media_progress_bar", "id", packageName),
                                resources.getIdentifier("media_elapsed_time", "id", packageName),
                                resources.getIdentifier("media_total_time", "id", packageName),
                                resources.getIdentifier("media_seamless", "id", packageName)});
                    IDS.put(resources, ids);
                }
            }
            boolean island = ids.islandBackground != 0 && root.findViewById(ids.islandBackground) != null;
            enabled = enabledPreset(island);
            if (selectedPreset(island)) {
                if (island || (ids.background != 0 && root.findViewById(ids.background) != null)) {
                    MediaConstraintPlan.hideSuppressedViews(root,
                            MediaConstraintCustomizer.presetXml(ModuleSettings.activeMediaPreset(island), island), island);
                }
            }
            if (!island && ModuleSettings.inFullAod && !ModuleSettings.disableMediaIslandHooks) {
                for (int i = 0; i < ids.aod.length; i++) {
                    if (i == 3 && !ModuleSettings.hideAodSeamless) continue;
                    View view = ids.aod[i] == 0 ? null : root.findViewById(ids.aod[i]);
                    if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
                }
                if (ModuleSettings.hideAodActions) for (int id : ids.values) {
                    View view = id == 0 ? null : root.findViewById(id);
                    if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
                }
            }
            for (int index = 0; index < ids.values.length; index++) {
                int id = ids.values[index];
                if (id == 0) continue;
                View button = root.findViewById(id);
                if (!(button instanceof ImageView image)) continue;
                if (enabled) {
                    if (!ORIGINALS.containsKey(button)) ORIGINALS.put(button, new Original(
                            button.getPaddingLeft(), button.getPaddingTop(), button.getPaddingRight(),
                            button.getPaddingBottom(), image.getScaleType()));
                    if (button.getPaddingLeft() != 0 || button.getPaddingTop() != 0
                            || button.getPaddingRight() != 0 || button.getPaddingBottom() != 0) {
                        button.setPadding(0, 0, 0, 0);
                    }
                    ImageView.ScaleType scale = "large_cover".equals(ModuleSettings.activeMediaPreset(island))
                            ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_INSIDE;
                    if (image.getScaleType() != scale) image.setScaleType(scale);
                } else {
                    Original original = ORIGINALS.remove(button);
                    if (original == null) continue;
                    button.setPadding(original.left, original.top, original.right, original.bottom);
                    image.setScaleType(original.scaleType);
                }
            }
        }
    }
    private record ActionIds(String packageName, int[] values, int background, int islandBackground, int[] aod) {}
    private record Original(int left, int top, int right, int bottom, ImageView.ScaleType scaleType) {}
}
