package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import android.graphics.Outline;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** View properties absent from ConstraintSet XML. Called only at media apply/binding boundaries. */
final class MediaLargeCoverStyle {
    private static final Map<Resources, Ids> IDS = new WeakHashMap<>();
    private static final Map<View, TextOriginal> TEXT = new WeakHashMap<>();
    // Both key and value are weak: an OEM outline provider may itself capture its owning View.
    private static final Map<View, WeakReference<CoverOutline>> COVERS = new WeakHashMap<>();

    static float coverRadius(float notification, float insetX, float insetY, float width, float height) {
        if (!Float.isFinite(notification)) return 0f;
        return Math.max(0f, Math.min(Math.min(width, height) / 2f,
                notification - Math.max(0f, Math.min(insetX, insetY))));
    }

    static synchronized void apply(View root) {
        boolean enabled = !ModuleSettings.disableMediaIslandHooks;
        boolean requested = enabled && ((!ModuleSettings.inFullAod && "large_cover".equals(ModuleSettings.activeMediaPreset(false)))
                || "large_cover".equals(ModuleSettings.activeMediaPreset(true)));
        if (!requested && TEXT.isEmpty() && COVERS.isEmpty()) return;
        Resources resources = root.getResources();
        Ids ids = IDS.get(resources);
        String pkg = root.getContext().getPackageName();
        if (ids == null || !ids.pkg.equals(pkg)) {
            ids = new Ids(pkg, id(resources, pkg, "media_bg"), id(resources, pkg, "media_bg_view"),
                    id(resources, pkg, "album_art"), id(resources, pkg, "album_art_image"),
                    id(resources, pkg, "header_title"), id(resources, pkg, "header_artist"),
                    id(resources, pkg, "media_elapsed_time"), id(resources, pkg, "media_total_time"),
                    id(resources, pkg, "media_progress_bar"),
                    new int[]{id(resources, pkg, "action0"), id(resources, pkg, "action1"), id(resources, pkg, "action2"),
                            id(resources, pkg, "action3"), id(resources, pkg, "action4")},
                    resources.getIdentifier("notification_item_bg_radius", "dimen", pkg));
            IDS.put(resources, ids);
        }
        View cover = find(root, ids.cover), image = find(root, ids.image);
        boolean mediaRoot = find(root, ids.background) != null || find(root, ids.islandBackground) != null;
        boolean island = find(root, ids.islandBackground) != null;
        enabled = enabled && (island || !ModuleSettings.inFullAod)
                && "large_cover".equals(ModuleSettings.activeMediaPreset(island));
        if (!enabled) {
            restoreCover(cover); restoreCover(image);
            restoreText(find(root, ids.title)); restoreText(find(root, ids.artist));
            restoreText(find(root, ids.elapsed)); restoreText(find(root, ids.total)); restoreText(find(root, ids.progress));
            MediaHyperProgressGeometry.restore(find(root, ids.progress));
            return;
        }
        if (!mediaRoot) return; // Album-only ConstraintSets are styled by their owning player.
        float density = resources.getDisplayMetrics().density;
        float fontDensity = resources.getDisplayMetrics().scaledDensity;
        int size = cover != null && cover.getLayoutParams() != null && cover.getLayoutParams().width > 0
                ? cover.getLayoutParams().width : Math.round((island ? Math.max(1, ModuleSettings.activeMediaPresetHeight(true) - 72) : 108) * density);
        float outerRadius = ModuleSettings.notificationsEnabled && Float.isFinite(ModuleSettings.notificationRadius)
                ? Math.max(0, ModuleSettings.notificationRadius) * density
                : ids.notificationRadius == 0 ? 28 * density : resources.getDimension(ids.notificationRadius);
        float insetX = 12 * density;
        if (cover != null && cover.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
            insetX = Math.max(0, margins.getMarginStart());
        }
        float insetY = Math.max(0, (ModuleSettings.activeMediaPresetHeight(island) * density - size) / 2f);
        float radius = coverRadius(outerRadius, insetX, insetY, size, size);
        round(cover, radius, 0);
        round(image, radius, size); // Island has no normalAlbumLayout.load(): resize its inner image directly.
        styleText(find(root, ids.title), 17 * fontDensity);
        styleText(find(root, ids.artist), 11 * fontDensity);
        styleText(find(root, ids.elapsed), 10 * fontDensity);
        styleText(find(root, ids.total), 10 * fontDensity);
        View progress = find(root, ids.progress);
        if (progress != null && !TEXT.containsKey(progress)) TEXT.put(progress, new TextOriginal(progress));
        MediaHyperProgressGeometry.apply(progress);
        String savedXml = island ? ModuleSettings.largeCoverMediaIslandXml : ModuleSettings.largeCoverMediaXml;
        if ((savedXml == null || savedXml.isBlank()) && progress != null
                && progress.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins
                && find(root, ids.title) != null && find(root, ids.title).getLayoutParams() instanceof ViewGroup.MarginLayoutParams textMargins) {
            int start = textMargins.getMarginStart() - MediaHyperProgressGeometry.trackInset(progress);
            if (margins.getMarginStart() != start) { margins.setMarginStart(start); progress.setLayoutParams(margins); }
        }
        if (!island && (savedXml == null || savedXml.isBlank())) {
            // Budget from actual font metrics, rather than allowing larger accessibility text
            // to overlap the action row. ConstraintSet reload restores stock button geometry.
            int available = Math.round(ModuleSettings.activeMediaPresetHeight(island) * density - 50 * density)
                    - lineHeight(find(root, ids.title)) - lineHeight(find(root, ids.artist)) - lineHeight(find(root, ids.elapsed));
            int primary = Math.max(Math.round(24 * density), Math.min(Math.round((island ? 40 : 36) * density), available));
            for (int i = 0; i < ids.actions.length; i++) {
                View button = find(root, ids.actions[i]);
                if (button == null || button.getLayoutParams() == null) continue;
                int inset = island ? Math.round((i == 2 ? 0 : i == 0 || i == 4 ? 4 : 2) * density) : 0;
                int height = Math.max(Math.round(24 * density), primary - inset);
                ViewGroup.LayoutParams params = button.getLayoutParams();
                if (params.height != height) { params.height = height; button.setLayoutParams(params); }
            }
        }
    }

    private static int lineHeight(View view) { return view instanceof TextView text ? text.getLineHeight() : 0; }

    private static int id(Resources r, String pkg, String name) { return r.getIdentifier(name, "id", pkg); }
    private static View find(View root, int id) { return id == 0 ? null : root.findViewById(id); }
    private static void styleText(View view, float textSize) {
        if (view == null) return;
        if (!TEXT.containsKey(view)) TEXT.put(view, new TextOriginal(view));
        if (view.getPaddingTop() != 0 || view.getPaddingBottom() != 0) {
            view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(), 0);
        }
        if (view.getMinimumHeight() != 0) view.setMinimumHeight(0);
        if (view instanceof TextView text) {
            if (Math.abs(text.getTextSize() - textSize) > .1f) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            if (text.getIncludeFontPadding()) text.setIncludeFontPadding(false);
            if (text.getMaxLines() != 1) text.setMaxLines(1);
            if (text.getEllipsize() != TextUtils.TruncateAt.END) text.setEllipsize(TextUtils.TruncateAt.END);
            if (text.getLineSpacingExtra() != 0 || text.getLineSpacingMultiplier() != 1) text.setLineSpacing(0, 1);
            if (text.getGravity() != (Gravity.START | Gravity.CENTER_VERTICAL)) text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        }
    }
    private static void restoreText(View view) {
        TextOriginal original = TEXT.remove(view);
        if (original == null) return;
        view.setPadding(original.left, original.top, original.right, original.bottom);
        view.setMinimumHeight(original.minHeight);
        if (view instanceof TextView text) {
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, original.textSize);
            text.setIncludeFontPadding(original.fontPadding); text.setMaxLines(original.maxLines);
            text.setEllipsize(original.ellipsize); text.setLineSpacing(original.spacing, original.multiplier);
            text.setGravity(original.gravity);
        }
    }
    private static void round(View view, float radius, int imageSize) {
        if (view == null) return;
        WeakReference<CoverOutline> ref = COVERS.get(view);
        CoverOutline outline = ref == null ? null : ref.get();
        if (outline == null || view.getOutlineProvider() != outline) {
            outline = new CoverOutline(view);
            COVERS.put(view, new WeakReference<>(outline));
            view.setOutlineProvider(outline);
        }
        if (outline.radius != radius) { outline.radius = radius; view.invalidateOutline(); }
        if (!view.getClipToOutline()) view.setClipToOutline(true);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (imageSize > 0 && params != null) {
            outline.lastSize = imageSize;
            if (params.width != imageSize || params.height != imageSize) {
                params.width = params.height = imageSize;
                view.setLayoutParams(params);
            }
        }
    }
    private static void restoreCover(View view) {
        WeakReference<CoverOutline> ref = COVERS.remove(view);
        CoverOutline original = ref == null ? null : ref.get();
        if (view == null || original == null || view.getOutlineProvider() != original) return;
        view.setOutlineProvider(original.provider); view.setClipToOutline(original.clipped);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (original.lastSize > 0 && params != null && params.width == original.lastSize && params.height == original.lastSize) {
            params.width = original.width; params.height = original.height; view.setLayoutParams(params);
        }
    }
    private static final class CoverOutline extends ViewOutlineProvider {
        final ViewOutlineProvider provider;
        final boolean clipped;
        final int width, height;
        float radius = -1;
        int lastSize;
        CoverOutline(View view) {
            provider = view.getOutlineProvider(); clipped = view.getClipToOutline();
            ViewGroup.LayoutParams params = view.getLayoutParams();
            width = params == null ? 0 : params.width; height = params == null ? 0 : params.height;
        }
        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    Math.min(radius, Math.min(view.getWidth(), view.getHeight()) / 2f));
        }
    }
    private static final class TextOriginal {
        final int left, top, right, bottom, minHeight, maxLines, gravity;
        final float textSize, spacing, multiplier;
        final boolean fontPadding;
        final TextUtils.TruncateAt ellipsize;
        TextOriginal(View view) {
            left = view.getPaddingLeft(); top = view.getPaddingTop(); right = view.getPaddingRight(); bottom = view.getPaddingBottom();
            minHeight = view.getMinimumHeight();
            TextView text = view instanceof TextView t ? t : null;
            maxLines = text == null ? 1 : text.getMaxLines(); gravity = text == null ? 0 : text.getGravity();
            textSize = text == null ? 0 : text.getTextSize(); spacing = text == null ? 0 : text.getLineSpacingExtra();
            multiplier = text == null ? 1 : text.getLineSpacingMultiplier(); fontPadding = text != null && text.getIncludeFontPadding();
            ellipsize = text == null ? null : text.getEllipsize();
        }
    }
    private record Ids(String pkg, int background, int islandBackground, int cover, int image,
                       int title, int artist, int elapsed, int total, int progress, int[] actions, int notificationRadius) {}
}
