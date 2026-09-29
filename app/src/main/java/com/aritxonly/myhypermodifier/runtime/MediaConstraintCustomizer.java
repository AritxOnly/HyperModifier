package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Outline;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.SeekBar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xmlpull.v1.XmlPullParser;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;
import static com.aritxonly.myhypermodifier.ResourceOverrides.*;
import static com.aritxonly.myhypermodifier.ControlCenterAppearance.*;
import static com.aritxonly.myhypermodifier.ReflectiveAccess.*;


/** Media ConstraintSet and seek-bar transformations. */
final class MediaConstraintCustomizer {
    private static final String TAG = "MyHyperModifier";
    private static final Map<View, Integer> ORIGINAL_MEDIA_ISLAND_LAYOUT_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> ORIGINAL_MEDIA_ISLAND_CAL_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> MEDIA_ISLAND_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Reproduces the edited miui_media_session_normal.xml ConstraintSet in the reference APK. */
    static void patchMediaConstraintSet(Context context, Object constraintSet, boolean island) {
        String preset = mediaLayoutPreset;
        if ("system".equals(preset) || "compact".equals(preset)
                || "standard".equals(preset) || "custom".equals(preset)) {
            if ("standard".equals(preset)) {
                patchLegacyMediaConstraintSet(context, constraintSet, island);
            }
            String xml = presetXml(preset, island);
            if (xml != null && !xml.trim().isEmpty()) {
                applyCustomMediaConstraintSet(context, constraintSet, xml, island);
            }
            int heightDp = presetHeight(preset);
            // This device's normal media stock resource is 185dp, while the island is 168dp.
            // The requested "system default" preset is explicitly 168dp for both surfaces.
            if (!island || !"system".equals(preset) || heightDp != 168
                    || (xml != null && !xml.trim().isEmpty())) {
                int background = id(context, island ? "media_bg_view" : "media_bg");
                setHeight(constraintSet, background, dp(context, heightDp));
            }
            return;
        }
        patchLegacyMediaConstraintSet(context, constraintSet, island);
    }

    private static String presetXml(String preset, boolean island) {
        switch (preset) {
            case "system": return island ? systemMediaIslandXml : systemMediaXml;
            case "compact": return island ? compactMediaIslandXml : compactMediaXml;
            case "standard": return island ? standardMediaIslandXml : standardMediaXml;
            default: return island ? customMediaIslandConstraintSetXml : customMediaConstraintSetXml;
        }
    }

    private static int presetHeight(String preset) {
        return activeMediaPresetHeight();
    }

    private static void patchLegacyMediaConstraintSet(Context context, Object constraintSet,
                                                       boolean island) {
        int parent = 0;
        int mediaBackground = id(context, island ? "media_bg_view" : "media_bg");
        int mediaBackgroundFallback = id(context, "media_bg");
        int albumArt = id(context, "album_art");
        int seamless = id(context, "media_seamless");
        int title = id(context, "header_title");
        int artist = id(context, "header_artist");
        int elapsed = id(context, "media_elapsed_time");
        int progress = id(context, "media_progress_bar");
        int total = id(context, "media_total_time");
        int actions = id(context, "actions");
        int[] action = {id(context, "action0"), id(context, "action1"), id(context, "action2"),
                id(context, "action3"), id(context, "action4")};

        // Do not replace expanded_island_height_dp globally: HyperOS also uses that integer for
        // non-media Super Island cards (for example, 12306).  The media island has its own
        // ConstraintSet, so changing its background here keeps this preference media-only.
        if (island && customMediaIslandConstraintSetEnabled
                && applyCustomMediaConstraintSet(context, constraintSet,
                customMediaIslandConstraintSetXml)) {
            return;
        }
        if (island && islandEnabled) {
            int height = resolvedMediaIslandHeight(context);
            setHeight(constraintSet, mediaBackground, height);
            setHeight(constraintSet, mediaBackgroundFallback, height);
        }
        if (!mediaEnabled) return;
        // ConstraintSet.load() only accepts an APK resource id, therefore an XML string saved by
        // the companion app is parsed after the stock set has loaded.
        if (!island && customMediaConstraintSetEnabled
                && applyCustomMediaConstraintSet(context, constraintSet, customMediaConstraintSetXml)) {
            setAodSeamlessConstraintVisibility(constraintSet, seamless);
            return;
        }
        if (!island || !islandEnabled) {
            setHeight(constraintSet, mediaBackground, dp(context, Math.round(expandedHeight)));
            setHeight(constraintSet, mediaBackgroundFallback, dp(context, Math.round(expandedHeight)));
        }

        Object seamlessLayout = layout(constraintSet, seamless);
        setInt(seamlessLayout, "topMargin", dp(context, 18));
        setInt(seamlessLayout, "endMargin", dp(context, 12));

        Object titleLayout = layout(constraintSet, title);
        Object artistLayout = layout(constraintSet, artist);
        setInt(titleLayout, "topMargin", dp(context, 18));
        if (island) {
            connect(titleLayout, "endToStart", action[4]);
            connect(artistLayout, "endToStart", action[4]);
        }

        Object progressLayout = layout(constraintSet, progress);
        setInt(progressLayout, "mWidth", 0);
        connect(progressLayout, "startToStart", parent);
        connect(progressLayout, "startToEnd", -1);
        connect(progressLayout, "endToEnd", parent);
        connect(progressLayout, "endToStart", -1);
        connect(progressLayout, "topToBottom", albumArt);
        connect(progressLayout, "topToTop", -1);
        connect(progressLayout, "bottomToTop", -1);
        connect(progressLayout, "bottomToBottom", -1);
        setInt(progressLayout, "topMargin", dimen(context, "media_progressbar_margin_top"));
        setInt(progressLayout, "startMargin", dp(context, 46));
        setInt(progressLayout, "endMargin", dp(context, 46));

        Object elapsedLayout = layout(constraintSet, elapsed);
        connect(elapsedLayout, "startToStart", parent);
        connect(elapsedLayout, "startToEnd", -1);
        connect(elapsedLayout, "endToStart", -1);
        connect(elapsedLayout, "endToEnd", -1);
        connect(elapsedLayout, "topToTop", progress);
        connect(elapsedLayout, "topToBottom", -1);
        connect(elapsedLayout, "bottomToBottom", progress);
        connect(elapsedLayout, "bottomToTop", -1);
        setInt(elapsedLayout, "startMargin", dp(context, 8));

        Object totalLayout = layout(constraintSet, total);
        connect(totalLayout, "endToEnd", parent);
        connect(totalLayout, "endToStart", -1);
        connect(totalLayout, "startToStart", -1);
        connect(totalLayout, "startToEnd", -1);
        connect(totalLayout, "topToTop", progress);
        connect(totalLayout, "topToBottom", -1);
        connect(totalLayout, "bottomToBottom", progress);
        connect(totalLayout, "bottomToTop", -1);
        setInt(totalLayout, "endMargin", dp(context, 8));

        Object actionsLayout = layout(constraintSet, actions);
        connect(actionsLayout, "topToBottom", progress);
        connect(actionsLayout, "topToTop", -1);
        setInt(actionsLayout, "topMargin", dp(context, 14));

        setActionConstraints(context, constraintSet, action, actions);
        setAodSeamlessConstraintVisibility(constraintSet, seamless);
    }

    /**
     * Applies the geometry-bearing attributes from a user supplied ConstraintSet XML document.
     * The stock set is loaded first, so omitted attributes retain MIUI's original value.  This
     * deliberately does not inflate views or accept arbitrary classes from XML.
     */
    static boolean applyCustomMediaConstraintSet(Context context, Object constraintSet,
                                                         String source) {
        return applyCustomMediaConstraintSet(context, constraintSet, source, false);
    }

    private static boolean applyCustomMediaConstraintSet(Context context, Object constraintSet,
                                                         String source, boolean island) {
        if (source == null || source.trim().isEmpty() || source.length() > 64 * 1024) {
            return false;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(source));
            boolean changed = false;
            int constraintCount = 0;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG
                        || !"Constraint".equals(parser.getName())) {
                    continue;
                }
                if (++constraintCount > 48) {
                    throw new IllegalArgumentException("Too many Constraint elements");
                }
                int viewId = parseConstraintTarget(context, parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "id"));
                if (island && viewId == id(context, "media_bg")) {
                    viewId = id(context, "media_bg_view");
                }
                Object targetLayout = layout(constraintSet, viewId);
                if (targetLayout == null) {
                    continue;
                }
                for (int index = 0; index < parser.getAttributeCount(); index++) {
                    if ("visibility".equals(parser.getAttributeName(index))) {
                        setConstraintVisibility(constraintSet, viewId,
                                parser.getAttributeValue(index));
                        continue;
                    }
                    applyCustomConstraintAttribute(context, targetLayout,
                            parser.getAttributeName(index), parser.getAttributeValue(index));
                }
                changed = true;
            }
            return changed;
        } catch (Throwable throwable) {
            Log.w(TAG, "Invalid custom media ConstraintSet XML; using bundled layout", throwable);
            return false;
        }
    }

    private static void setConstraintVisibility(Object constraintSet, int viewId, String value) {
        int visibility;
        if ("gone".equals(value) || "8".equals(value)) visibility = View.GONE;
        else if ("invisible".equals(value) || "4".equals(value)) visibility = View.INVISIBLE;
        else if ("visible".equals(value) || "0".equals(value)) visibility = View.VISIBLE;
        else return;
        try {
            constraintSet.getClass().getMethod("setVisibility", int.class, int.class)
                    .invoke(constraintSet, viewId, visibility);
        } catch (ReflectiveOperationException error) {
            Log.w(TAG, "ConstraintSet visibility override unavailable", error);
        }
    }

    /**
     * Returns the explicit media-island background height requested by its custom XML, or zero
     * when the XML leaves its height at 0dp/match-constraints. In the latter case the dedicated
     * Super Island height slider remains the single source of truth for the outer root.
     */
    private static int customMediaIslandHeight(Context context) {
        String source = customMediaIslandConstraintSetXml;
        if (!customMediaIslandConstraintSetEnabled || source == null || source.trim().isEmpty()
                || source.length() > 64 * 1024) {
            return 0;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(source));
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG
                        || !"Constraint".equals(parser.getName())) {
                    continue;
                }
                String target = parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "id");
                int id = parseConstraintTarget(context, target);
                if (id != id(context, "media_bg_view") && id != id(context, "media_bg")) {
                    continue;
                }
                String height = parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "layout_height");
                int pixels = height == null ? 0 : parseDimension(context, height);
                return Math.max(0, pixels);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to read media island XML height", throwable);
        }
        return 0;
    }

    /** Resolves the height applied to both the media island ConstraintSet and its root. */
    static int resolvedMediaIslandHeight(Context context) {
        int customHeight = customMediaIslandHeight(context);
        return customHeight > 0 ? customHeight : dp(context, islandHeight);
    }

    /** Zero means leave the stock host geometry untouched. */
    static int requestedMediaIslandHeight(Context context) {
        String preset = mediaLayoutPreset;
        if ("system".equals(preset) || "compact".equals(preset)
                || "standard".equals(preset) || "custom".equals(preset)) {
            int height = presetHeight(preset);
            return "system".equals(preset) && height == 168 ? 0 : dp(context, height);
        }
        int customHeight = customMediaIslandHeight(context);
        return customHeight > 0 ? customHeight
                : islandEnabled ? dp(context, islandHeight) : 0;
    }

    /**
     * Synchronizes PlayerIslandConstraintLayout's own measured/root height with its background.
     * The separate plugin host geometry is handled by MediaIslandHostHooks; changing this
     * player root alone does not resize the plugin's translucent backdrop.
     */
    static void synchronizeMediaIslandRootHeight(View island) {
        if (island == null) return;
        int height = requestedMediaIslandHeight(island.getContext());
        ViewGroup.LayoutParams layoutParams = island.getLayoutParams();
        if (height <= 0) {
            Integer originalCalHeight;
            Integer originalLayoutHeight;
            synchronized (ORIGINAL_MEDIA_ISLAND_CAL_HEIGHTS) {
                originalCalHeight = ORIGINAL_MEDIA_ISLAND_CAL_HEIGHTS.remove(island);
            }
            synchronized (ORIGINAL_MEDIA_ISLAND_LAYOUT_HEIGHTS) {
                originalLayoutHeight = ORIGINAL_MEDIA_ISLAND_LAYOUT_HEIGHTS.remove(island);
            }
            if (originalCalHeight != null) {
                setDeclaredInt(island, "calHeight", originalCalHeight);
            }
            if (layoutParams != null && originalLayoutHeight != null
                    && layoutParams.height != originalLayoutHeight) {
                layoutParams.height = originalLayoutHeight;
                island.setLayoutParams(layoutParams);
            }
            return;
        }
        synchronized (ORIGINAL_MEDIA_ISLAND_CAL_HEIGHTS) {
            if (!ORIGINAL_MEDIA_ISLAND_CAL_HEIGHTS.containsKey(island)) {
                ORIGINAL_MEDIA_ISLAND_CAL_HEIGHTS.put(island,
                        declaredIntField(island, "calHeight", height));
            }
        }
        setDeclaredInt(island, "calHeight", height);
        if (layoutParams == null) return;
        synchronized (ORIGINAL_MEDIA_ISLAND_LAYOUT_HEIGHTS) {
            if (!ORIGINAL_MEDIA_ISLAND_LAYOUT_HEIGHTS.containsKey(island)) {
                ORIGINAL_MEDIA_ISLAND_LAYOUT_HEIGHTS.put(island, layoutParams.height);
            }
        }
        if (layoutParams.height != height) {
            layoutParams.height = height;
            island.setLayoutParams(layoutParams);
        }
    }

    /** Keep both the visible and dummy PlayerIsland roots available after asynchronous settings load. */
    static void rememberMediaIslandRoot(View island) {
        if (island == null) return;
        synchronized (MEDIA_ISLAND_ROOTS) {
            MEDIA_ISLAND_ROOTS.put(island, Boolean.TRUE);
        }
    }

    /** Re-measure every cached island root once remote preferences become available. */
    static void refreshMediaIslandRoots() {
        List<View> roots;
        synchronized (MEDIA_ISLAND_ROOTS) {
            roots = new ArrayList<>(MEDIA_ISLAND_ROOTS.keySet());
        }
        for (View root : roots) {
            if (root == null) continue;
            root.post(() -> {
                synchronizeMediaIslandRootHeight(root);
                root.requestLayout();
            });
        }
    }

    static void applyCustomConstraintAttribute(Context context, Object targetLayout,
                                                       String name, String value) {
        if (name == null || value == null) return;
        switch (name) {
            case "layout_width": setInt(targetLayout, "mWidth", parseDimension(context, value)); return;
            case "layout_height": setInt(targetLayout, "mHeight", parseDimension(context, value)); return;
            case "layout_margin":
                int margin = parseDimension(context, value);
                setInt(targetLayout, "leftMargin", margin); setInt(targetLayout, "rightMargin", margin);
                setInt(targetLayout, "topMargin", margin); setInt(targetLayout, "bottomMargin", margin);
                return;
            case "layout_marginStart": setInt(targetLayout, "startMargin", parseDimension(context, value)); return;
            case "layout_marginEnd": setInt(targetLayout, "endMargin", parseDimension(context, value)); return;
            case "layout_marginLeft": setInt(targetLayout, "leftMargin", parseDimension(context, value)); return;
            case "layout_marginRight": setInt(targetLayout, "rightMargin", parseDimension(context, value)); return;
            case "layout_marginTop": setInt(targetLayout, "topMargin", parseDimension(context, value)); return;
            case "layout_marginBottom": setInt(targetLayout, "bottomMargin", parseDimension(context, value)); return;
            case "layout_constraintStart_toStartOf": connect(targetLayout, "startToStart", parseConstraintTarget(context, value)); return;
            case "layout_constraintStart_toEndOf": connect(targetLayout, "startToEnd", parseConstraintTarget(context, value)); return;
            case "layout_constraintEnd_toStartOf": connect(targetLayout, "endToStart", parseConstraintTarget(context, value)); return;
            case "layout_constraintEnd_toEndOf": connect(targetLayout, "endToEnd", parseConstraintTarget(context, value)); return;
            case "layout_constraintLeft_toLeftOf": connect(targetLayout, "leftToLeft", parseConstraintTarget(context, value)); return;
            case "layout_constraintLeft_toRightOf": connect(targetLayout, "leftToRight", parseConstraintTarget(context, value)); return;
            case "layout_constraintRight_toLeftOf": connect(targetLayout, "rightToLeft", parseConstraintTarget(context, value)); return;
            case "layout_constraintRight_toRightOf": connect(targetLayout, "rightToRight", parseConstraintTarget(context, value)); return;
            case "layout_constraintTop_toTopOf": connect(targetLayout, "topToTop", parseConstraintTarget(context, value)); return;
            case "layout_constraintTop_toBottomOf": connect(targetLayout, "topToBottom", parseConstraintTarget(context, value)); return;
            case "layout_constraintBottom_toTopOf": connect(targetLayout, "bottomToTop", parseConstraintTarget(context, value)); return;
            case "layout_constraintBottom_toBottomOf": connect(targetLayout, "bottomToBottom", parseConstraintTarget(context, value)); return;
            case "layout_constraintBaseline_toBaselineOf": connect(targetLayout, "baselineToBaseline", parseConstraintTarget(context, value)); return;
            case "layout_constraintHorizontal_bias": setFloat(targetLayout, "horizontalBias", parseFloat(value)); return;
            case "layout_constraintVertical_bias": setFloat(targetLayout, "verticalBias", parseFloat(value)); return;
            case "layout_constraintHorizontal_chainStyle": setInt(targetLayout, "horizontalChainStyle", parseChainStyle(value)); return;
            case "layout_constraintVertical_chainStyle": setInt(targetLayout, "verticalChainStyle", parseChainStyle(value)); return;
            case "layout_constraintDimensionRatio": setString(targetLayout, "dimensionRatio", value); return;
            case "layout_constraintWidth_percent": setFloat(targetLayout, "matchConstraintPercentWidth", parseFloat(value)); return;
            case "layout_constraintHeight_percent": setFloat(targetLayout, "matchConstraintPercentHeight", parseFloat(value)); return;
            case "layout_constraintGuide_begin": setInt(targetLayout, "guideBegin", parseDimension(context, value)); return;
            case "layout_constraintGuide_end": setInt(targetLayout, "guideEnd", parseDimension(context, value)); return;
            case "layout_constraintGuide_percent": setFloat(targetLayout, "guidePercent", parseFloat(value)); return;
            default:
                // Text styling and any unknown/new MIUI ConstraintLayout attributes remain stock.
        }
    }

    static int parseConstraintTarget(Context context, String value) {
        if (value == null || "parent".equals(value)) return 0;
        if ("-1".equals(value)) return -1;
        String name = value.startsWith("@") ? value.substring(value.indexOf('/') + 1) : value;
        return id(context, name);
    }

    static int parseDimension(Context context, String value) {
        String dimension = value.trim();
        if ("wrap_content".equals(dimension)) return -2;
        if ("match_parent".equals(dimension) || "fill_parent".equals(dimension)) return -1;
        if (dimension.startsWith("@dimen/")) return dimen(context, dimension.substring(7));
        float multiplier = 1f;
        if (dimension.endsWith("dip") || dimension.endsWith("dp")) {
            multiplier = context.getResources().getDisplayMetrics().density;
            dimension = dimension.replaceFirst("(dip|dp)$", "");
        } else if (dimension.endsWith("sp")) {
            multiplier = context.getResources().getDisplayMetrics().scaledDensity;
            dimension = dimension.substring(0, dimension.length() - 2);
        } else if (dimension.endsWith("px")) {
            dimension = dimension.substring(0, dimension.length() - 2);
        }
        return Math.round(Float.parseFloat(dimension) * multiplier);
    }

    static float parseFloat(String value) {
        return Float.parseFloat(value.trim());
    }

    static int parseChainStyle(String value) {
        if ("spread_inside".equals(value)) return 1;
        if ("packed".equals(value)) return 2;
        return 0;
    }

    static void setActionConstraints(Context context, Object constraintSet, int[] action, int actions) {
        if (action[0] == 0) {
            return;
        }
        Object first = layout(constraintSet, action[0]);
        connect(first, "topToTop", actions);
        connect(first, "topToBottom", -1);
        connect(first, "bottomToBottom", actions);
        connect(first, "bottomToTop", -1);
        connect(first, "leftToLeft", actions);
        connect(first, "leftToRight", -1);
        connect(first, "rightToLeft", action[1]);
        connect(first, "rightToRight", -1);
        setInt(first, "topMargin", 0);
        setInt(first, "startMargin", 0);
        setInt(first, "horizontalChainStyle", 0);

        for (int index = 1; index < action.length; index++) {
            Object current = layout(constraintSet, action[index]);
            connect(current, "topToTop", action[0]);
            connect(current, "topToBottom", -1);
            connect(current, "bottomToBottom", action[0]);
            connect(current, "bottomToTop", -1);
        }
        setInt(layout(constraintSet, action[1]), "endMargin", dp(context, 5));
        setInt(layout(constraintSet, action[3]), "startMargin", dp(context, 5));
        setInt(layout(constraintSet, action[4]), "endMargin", 0);
    }

    /** Mirrors the reference dex: hide in full AOD and restore as soon as it exits. */
    static void setAodActionsVisibility(Object controller, boolean inFullAod) {
        if (!hideAodActions) return;
        Object holder = fieldValue(controller, "holder");
        for (int index = 0; index < 5; index++) {
            Object action = fieldValue(holder, "action" + index);
            if (action instanceof View) {
                ((View) action).setVisibility(inFullAod ? View.GONE : View.VISIBLE);
            }
        }
    }

    /** Keeps media transfer hidden through AOD state changes and media-data refreshes. */
    static void setAodSeamlessVisibility(Object controller, boolean inFullAod) {
        if (!hideAodSeamless) return;
        Object holder = fieldValue(controller, "holder");
        Object seamless = fieldValue(holder, "seamless");
        if (seamless instanceof View) {
            ((View) seamless).setVisibility(inFullAod ? View.GONE : View.VISIBLE);
        }
    }

    /** Applies the same policy to a ConstraintSet loaded while Full AOD is already active. */
    static void setAodSeamlessConstraintVisibility(Object constraintSet, int seamless) {
        if (hideAodSeamless && inFullAod && seamless != 0) {
            setInt(layout(constraintSet, seamless), "mVisibility", View.GONE);
        }
    }

    /** Copies the island SeekProgressBar's runtime attributes onto the standard media seek bar. */
    static void configureSeekBar(Object holder) {
        Object seekBar = fieldValue(holder, "seekBar");
        if (!(seekBar instanceof View)) {
            return;
        }
        View view = (View) seekBar;
        view.setFocusable(true);
        invokeBoolean(seekBar, "setIndeterminate", false);
        invokeBoolean(seekBar, "setMirrorForRtl", true);
        invokeInt(seekBar, "setBackgroundPrimaryColor", Color.argb(0x1a, 0xff, 0xff, 0xff));
        invokeInt(seekBar, "setForegroundPrimaryColor", Color.WHITE);
        setInt(seekBar, "mProgressAlpha", 153);
        setInt(seekBar, "mDrawProgressAlpha", 153);
        setInt(seekBar, "mProgressPressedAlpha", 230);
        view.invalidate();
    }

    /**
     * Runtime equivalent of replacing the normal layout's SeekProgressBar with the island one.
     * This runs only after XML inflation and immediately before attach() wires touch handling,
     * avoiding a global LayoutInflater/constructor hook during SystemUI startup.
     */
    static void replaceNormalSeekBar(Object holder) {
        Object current = fieldValue(holder, "seekBar");
        if (!(current instanceof SeekBar) || intField(current, "mProgressMode", -1) != 0) {
            return;
        }

        View oldView = (View) current;
        if (!(oldView.getParent() instanceof ViewGroup)) {
            return;
        }

        try {
            Object replacement = current.getClass().getConstructor(Context.class)
                    .newInstance(oldView.getContext());
            if (!(replacement instanceof SeekBar)) {
                return;
            }

            SeekBar oldSeekBar = (SeekBar) current;
            SeekBar newSeekBar = (SeekBar) replacement;
            View newView = (View) replacement;
            ViewGroup parent = (ViewGroup) oldView.getParent();
            int position = parent.indexOfChild(oldView);
            ViewGroup.LayoutParams layoutParams = oldView.getLayoutParams();
            if (position < 0 || layoutParams == null) {
                return;
            }

            newView.setId(oldView.getId());
            newView.setLayoutDirection(oldView.getLayoutDirection());
            newView.setVisibility(oldView.getVisibility());
            newView.setEnabled(oldView.isEnabled());
            newView.setAlpha(oldView.getAlpha());
            newView.setContentDescription(oldView.getContentDescription());
            newView.setPaddingRelative(oldView.getPaddingStart(), oldView.getPaddingTop(),
                    oldView.getPaddingEnd(), oldView.getPaddingBottom());
            newSeekBar.setMax(oldSeekBar.getMax());
            newSeekBar.setProgress(oldSeekBar.getProgress());
            newSeekBar.setSecondaryProgress(oldSeekBar.getSecondaryProgress());

            parent.removeViewAt(position);
            parent.addView(newView, position, layoutParams);
            setFieldValue(holder, "seekBar", replacement);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Leave the original widget untouched if the target SystemUI revision differs.
            Log.w(TAG, "Could not replace normal media seek bar", exception);
        }
    }

    static int id(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }

    static int dimen(Context context, String name) {
        int resourceId = context.getResources().getIdentifier(name, "dimen", context.getPackageName());
        return resourceId == 0 ? 0 : context.getResources().getDimensionPixelSize(resourceId);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void setHeight(Object constraintSet, int viewId, int height) {
        setInt(layout(constraintSet, viewId), "mHeight", height);
    }

}
