package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Targets the view and shader contracts verified in reference/Settings.apk. */
final class AboutPhoneHooks {
    private static final String PREFIX = "com.android.settings.device.";
    private static final String RING_TAG = "mhm-about-storage-ring";
    private static final String PAIR_TAG = "mhm-about-card-pair";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Map<View, Float> STORAGE = Collections.synchronizedMap(new WeakHashMap<>());
    private static Bitmap phoneBitmap;
    private static String phoneBitmapKey;

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Method created = Class.forName(PREFIX + "MiuiMyDeviceSettings", false, loader)
                    .getDeclaredMethod("onViewCreated", View.class, android.os.Bundle.class);
            module.hook(created).setId("about-phone-card-layout")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        View root = (View) chain.getArg(0);
                        WeakReference<View> reference = new WeakReference<>(root);
                        ModuleSettings.ensureLoaded();
                        ModuleSettings.onLoaded(() -> {
                            View view = reference.get();
                            if (view != null) view.post(() -> {
                                if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.aboutPhoneCardsEnabled) return;
                                try { arrangeCards(view); }
                                catch (Throwable error) { module.log(Log.WARN, "MyHyperModifier", "About phone layout unavailable", error); }
                            });
                        });
                        return result;
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, "MyHyperModifier", "About phone layout hook unavailable", error);
            HookDiagnostics.failure("com.android.settings", "关于手机布局", error);
        }
        try {
            Class<?> callback = Class.forName(PREFIX + "MiuiMemoryCard$MemoryInfoCallback", false, loader);
            Field outer = field(callback, "mOuterRef");
            Class<?> utils = Class.forName(PREFIX + "MiuiAboutPhoneUtils", false, loader);
            Method instance = utils.getDeclaredMethod("getInstance", Context.class);
            Method total = utils.getDeclaredMethod("getTotalMemoryBytes");
            module.hook(callback.getDeclaredMethod("handleTaskResult", long.class))
                    .setId("about-phone-storage-usage")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        View card = (View) ((WeakReference<?>) outer.get(chain.getThisObject())).get();
                        if (card != null) {
                            long bytes = (Long) total.invoke(instance.invoke(null, card.getContext()));
                            float fraction = AboutPhoneAppearancePolicy.storageFraction(bytes, (Long) chain.getArg(0));
                            STORAGE.put(card, fraction);
                            card.post(() -> {
                                StorageRing ring = card.findViewWithTag(RING_TAG);
                                if (ring != null) { ring.fraction = fraction; ring.invalidate(); }
                            });
                        }
                        return result;
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, "MyHyperModifier", "About phone storage ring unavailable", error);
            HookDiagnostics.failure("com.android.settings", "存储占用环", error);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static View named(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.settings");
        return id == 0 ? null : root.findViewById(id);
    }

    private static int dp(View view, float value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static void arrangeCards(View root) {
        if (root.findViewWithTag(PAIR_TAG) != null) return;
        View basicView = named(root, "device_basic_layout");
        View nameView = named(root, "device_name_card_view");
        View memoryView = named(root, "device_memory_card_view");
        if (!(basicView instanceof LinearLayout basic) || !(nameView instanceof FrameLayout name)
                || !(memoryView instanceof FrameLayout memory) || !(basic.getParent() instanceof LinearLayout scroll)
                || name.getParent() != basic || memory.getParent() != basic) return;
        // Validate all resources and views before touching the stock hierarchy.
        TextView nameTitle = (TextView) named(name, "title");
        TextView nameSummary = (TextView) named(name, "summary");
        TextView memoryTitle = (TextView) named(memory, "title");
        TextView memorySummary = (TextView) named(memory, "summary");
        int background = root.getResources().getIdentifier("new_device_card_back_ground", "drawable", "com.android.settings");
        if (nameTitle == null || nameSummary == null || memoryTitle == null || memorySummary == null || background == 0) return;
        Drawable nameBackground = root.getContext().getDrawable(background).mutate();
        Drawable memoryBackground = root.getContext().getDrawable(background).mutate();
        LinearLayout pair = new LinearLayout(root.getContext());
        pair.setTag(PAIR_TAG);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setBaselineAligned(false);
        LinearLayout.LayoutParams pairParams = new LinearLayout.LayoutParams(basic.getLayoutParams());
        if (basic.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
            pairParams.setMarginStart(margins.getMarginStart());
            pairParams.setMarginEnd(margins.getMarginEnd());
        }
        pairParams.topMargin = -dp(root, 24);
        pairParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        basic.removeView(name);
        basic.removeView(memory);
        styleCard(name, nameTitle, nameSummary, false, nameBackground);
        styleCard(memory, memoryTitle, memorySummary, true, memoryBackground);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        left.setMarginEnd(dp(root, 6));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        right.setMarginStart(dp(root, 6));
        pair.addView(name, left);
        pair.addView(memory, right);
        scroll.addView(pair, scroll.indexOfChild(basic), pairParams);
        LinearLayout.LayoutParams remaining = (LinearLayout.LayoutParams) basic.getLayoutParams();
        remaining.topMargin = dp(root, 12);
        basic.setLayoutParams(remaining);
        // All remaining rows and the OS header keep their original views and styling.
    }

    private static void styleCard(FrameLayout card, TextView title, TextView summary, boolean storage, Drawable background) {
        ((ViewGroup) title.getParent()).removeView(title);
        ((ViewGroup) summary.getParent()).removeView(summary);
        card.removeAllViews();
        card.setPadding(0, 0, 0, 0);
        card.setBackground(background);
        card.setClipToOutline(true);
        LinearLayout content = new LinearLayout(card.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPaddingRelative(dp(card, 14), dp(card, 12), dp(card, 14), dp(card, 12));
        // The stock card title can use the accent color; values should follow the page theme.
        android.content.res.TypedArray textColors = card.getContext().obtainStyledAttributes(
                new int[] { android.R.attr.textColorPrimary });
        android.content.res.ColorStateList foreground;
        try {
            foreground = textColors.getColorStateList(0);
        } finally {
            textColors.recycle();
        }
        if (foreground == null) {
            boolean dark = (card.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            foreground = android.content.res.ColorStateList.valueOf(dark ? 0xffeeeeee : 0xff111111);
        }
        title.setTextSize(13);
        title.setTextColor(summary.getTextColors());
        title.setSingleLine(true);
        title.setMaxLines(1);
        title.setGravity(Gravity.START);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        summary.setTextSize(18);
        summary.setTypeface(summary.getTypeface(), Typeface.BOLD);
        summary.setSingleLine(true);
        summary.setMaxLines(1);
        summary.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        summary.setMarqueeRepeatLimit(-1);
        summary.setHorizontalFadingEdgeEnabled(true);
        summary.setSelected(true);
        summary.setGravity(Gravity.START);
        summary.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        summary.setTextColor(foreground);
        if (storage) {
            summary.setText(AboutPhoneAppearancePolicy.storageLabel(summary.getText().toString()));
            // Stock callbacks continue updating this TextView; remove the suffix on every update.
            summary.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable text) {
                    String clean = AboutPhoneAppearancePolicy.storageLabel(text.toString());
                    if (!clean.contentEquals(text)) text.replace(0, text.length(), clean);
                }
            });
        }
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.topMargin = dp(card, 3);
        content.addView(summary, valueParams);
        View illustration;
        if (storage) {
            StorageRing ring = new StorageRing(card.getContext());
            ring.fraction = STORAGE.getOrDefault(card, 0f);
            ring.setTag(RING_TAG);
            illustration = ring;
        } else {
            PhoneImageView phone = new PhoneImageView(card.getContext());
            phone.setForegroundColor(summary.getCurrentTextColor());
            phone.setImage(loadPhoneBitmap());
            String source = ModuleSettings.aboutPhoneImageSource;
            String key = "auto".equals(source) ? PhoneProductPolicy.currentAsset() : source;
            float[] transform = PhoneImageProfile.read(ModuleSettings.aboutPhoneImageTransforms, key == null ? "auto" : key);
            phone.setTransform(transform[0], transform[1], transform[2]);
            illustration = phone;
        }
        illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        FrameLayout.LayoutParams illustrationParams = new FrameLayout.LayoutParams(
                dp(card, 64), dp(card, 64), Gravity.BOTTOM | Gravity.START);
        illustrationParams.setMarginStart(dp(card, 14));
        illustrationParams.bottomMargin = dp(card, 12);
        card.addView(illustration, illustrationParams);
        // Fixed graphic anchors are independent of text width and match on both cards.
        // Grow both equally with large system fonts so the heading never overlaps the graphic.
        card.setMinimumHeight(Math.max(dp(card, 148),
                title.getLineHeight() + summary.getLineHeight() + dp(card, 107)));
        // Keep the original card's onClick listener, enabled state and data TextView references.
    }

    private static final class StorageRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float fraction;
        StorageRing(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float stroke = getWidth() * 0.16f;
            float inset = stroke / 2;
            RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0x207f7f7f);
            canvas.drawOval(bounds, paint);
            paint.setColor(0xff6688ff);
            if (fraction > 0) canvas.drawArc(bounds, -90, fraction * 360, false, paint);
        }
    }

    private static synchronized Bitmap loadPhoneBitmap() {
        String source = ModuleSettings.aboutPhoneImageSource;
        String selected = "auto".equals(source) ? PhoneProductPolicy.currentAsset() : source;
        String encoded = "custom".equals(source) ? ModuleSettings.aboutPhoneCustomImage
                : java.util.Objects.equals(selected, ModuleSettings.aboutPhonePresetImageKey)
                ? ModuleSettings.aboutPhonePresetImage : "";
        String key = selected + ":" + encoded;
        if (key.equals(phoneBitmapKey)) return phoneBitmap;
        phoneBitmapKey = key;
        phoneBitmap = null;
        try {
            if (encoded.isEmpty() || encoded.length() > 128 * 1024) return null;
            byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            phoneBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Throwable error) {
            Log.w("MyHyperModifier", "Phone product illustration unavailable", error);
        }
        return phoneBitmap;
    }
}
