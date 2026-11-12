package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** HMC MiniPlayerRuntime.material's notification-style path, detached from the music player. */
final class HmcShortcutMaterial implements ShortcutMaterial {
    private final Method containerBlur, radius, winType, blend, glass, type;
    private final ThreadLocal<Capture> capture = new ThreadLocal<>();
    private Configuration configuration;
    private int small, big;
    private int[] colors;
    private float[] params;
    private Drawable.ConstantState background;
    private int generation;
    private List<HmcMaterialCall> recipe = java.util.Collections.emptyList();
    private static final class Capture {
        View target;
        int depth, recordDepth;
        final ArrayList<HmcMaterialCall> calls = new ArrayList<>();
    }

    HmcShortcutMaterial(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> util = Class.forName("com.android.systemui.statusbar.notification.utils.NotificationUtil", false, loader);
        Class<?> compat = Class.forName("com.miui.systemui.util.MiGlassCompat", false, loader);
        containerBlur = util.getMethod("applyContainerViewBlur", Context.class, View.class, int.class, int.class, boolean.class);
        radius = View.class.getMethod("setMiGlassBlurRadius", int.class, int.class);
        blend = util.getMethod("applyElementViewBlend", Context.class, View.class, boolean.class, int[].class, boolean.class);
        glass = compat.getMethod("setMiGlassCompat", View.class, float[].class);
        type = compat.getMethod("setMiViewMaterialTypeCompat", int.class, View.class);
        Method win = null;
        try { win = Class.forName("miuix.core.util.MiuiBlurUtils", false, loader).getMethod("setMiBlurWinType", View.class); }
        catch (ReflectiveOperationException ignored) { }
        winType = win;
    }

    public int currentGeneration() { return generation; }

    public int generation(Context context) {
        if (!context.getResources().getConfiguration().equals(configuration)) {
            Resources res = context.getResources();
            small = res.getDimensionPixelSize(id(res, "dimen", "notification_glass_small_blur_max_radius"));
            big = res.getDimensionPixelSize(id(res, "dimen", "notification_glass_big_blur_max_radius"));
            // HMC normally has a recorded card before drawing its discs. Without a music player,
            // seed from the same native keyguard effect, then replace it with live recorded calls.
            String[] values = res.getStringArray(id(res, "array", "notification_glass_params_on_keyguard"));
            params = new float[values.length];
            if (values.length != 42) throw new IllegalStateException("Unsupported keyguard glass recipe");
            for (int i = 0; i < values.length; i++) params[i] = Float.parseFloat(values[i]);
            colors = new int[] {
                res.getColor(id(res, "color", "notification_element_blend_keyguard_color_1"), context.getTheme()),
                res.getInteger(id(res, "integer", "notification_element_blend_keyguard_mode_1")),
                res.getColor(id(res, "color", "notification_element_blend_keyguard_color_2"), context.getTheme()),
                res.getInteger(id(res, "integer", "notification_element_blend_keyguard_mode_2"))};
            background = context.getDrawable(id(res, "drawable", "notification_heads_up_transparent_bg")).getConstantState();
            configuration = new Configuration(res.getConfiguration());
            recipe = java.util.Collections.emptyList();
            generation++;
        }
        return generation;
    }

    // Directly carries the HMC container + element sequence. Only cached metadata replaces
    // repeated getMethod/Class.forName, and the music-specific recipe/selection code is omitted.
    public void material(ImageView view) throws ReflectiveOperationException {
        Context ctx = view.getContext();
        generation(ctx);
        if (view.getParent() instanceof View) {
            View container = (View) view.getParent();
            containerBlur.invoke(null, ctx, container, small, 0, false);
            radius.invoke(container, small, big);
            if (winType != null) winType.invoke(null, container);
        }
        view.setImageDrawable(null);
        view.setBackground(background == null ? null : background.newDrawable(ctx.getResources()).mutate());
        for (HmcMaterialCall call : recipe) call.replay(ctx, view);
        blend.invoke(null, ctx, view, false, colors.clone(), false);
        type.invoke(null, 1, view);
        glass.invoke(null, view, (Object) params.clone());
    }

    void installRecorder(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> row = Class.forName("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", false, loader);
        Method injector = row.getMethod("getInjector");
        Method bg = injector.getReturnType().getMethod("getBackgroundNormal");
        for (String name : new String[] {"NotificationRowGlassOnKeyguardEffect", "NotificationRowGlassOnKeyguardLightWallPaperEffect"}) {
            Class<?> effect = Class.forName("com.android.systemui.statusbar.notification.style.vieweffect." + name, false, loader);
            Method apply = effect.getMethod("apply", Object.class, Context.class);
            module.hook(apply).setId("shortcut-material-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (!LockscreenShortcutGlassHooks.enabled()) return chain.proceed();
                        generation((Context) chain.getArg(1));
                        Capture session = capture.get();
                        if (session == null) { session = new Capture(); capture.set(session); }
                        if (session.depth != 0) return chain.proceed();
                        session.calls.clear();
                        Object rowInjector = injector.invoke(chain.getArg(0));
                        if (rowInjector == null) return chain.proceed();
                        session.target = (View) bg.invoke(rowInjector);
                        if (session.target == null) return chain.proceed();
                        session.depth++;
                        boolean complete = false;
                        try {
                            Object result = chain.proceed();
                            complete = true;
                            return result;
                        }
                        finally {
                            session.target = null;
                            session.depth--;
                            boolean changed = recipe.size() != session.calls.size();
                            if (!changed) for (int i = 0; i < recipe.size(); i++) {
                                if (recipe.get(i) != session.calls.get(i)) { changed = true; break; }
                            }
                            // As in HMC, an empty recording must never erase a usable recipe.
                            if (complete && changed && !session.calls.isEmpty()) {
                                recipe = new ArrayList<>(session.calls);
                                generation++;
                            }
                            session.calls.clear();
                        }
                    });
        }
        // Same outermost native calls recorded by HMC installCardMaterialHooks. Their
        // metadata is resolved here once; the disabled/non-recording path allocates nothing.
        Map<String, Set<String>> names = new LinkedHashMap<>();
        names.put("com.android.systemui.statusbar.notification.utils.NotificationUtil", Set.of("applyElementViewBlend"));
        names.put("com.miui.systemui.util.MiGlassCompat", Set.of("setMiGlassBlurRadius", "setMiGlassCompat",
                "setMiGlassSdfMaxSizeCompat", "setMiViewMaterialTypeCompat"));
        names.put("com.miui.systemui.util.MiBlurCompat", Set.of("addMiBackgroundBlendColorCompat",
                "clearMiBackgroundBlendColorCompat", "setBlurOutlineEnable", "setGlassOutlineEnable",
                "setMiBackgroundBlendColors", "setMiBackgroundBlendColors$default",
                "setMiBackgroundBlendColorsNew$default", "setMiBackgroundBlurModeCompat",
                "setMiBackgroundBlurRadiusCompat", "setMiBackgroundBlurScaleRatioCompat",
                "setMiSelfBlurTypeCompat", "setMiViewBlurModeCompat", "setPassWindowBlurEnabledCompat"));
        for (Map.Entry<String, Set<String>> group : names.entrySet()) {
            Class<?> owner = Class.forName(group.getKey(), false, loader);
            for (Method method : owner.getDeclaredMethods()) {
                if (!group.getValue().contains(method.getName()) || !Modifier.isStatic(method.getModifiers())) continue;
                HmcMaterialCall.Plan plan = new HmcMaterialCall.Plan(method);
                if (plan.viewAt < 0) continue;
                method.setAccessible(true);
                module.hook(method).setId("shortcut-record-" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Capture session = capture.get();
                            if (session == null || session.target == null || session.recordDepth != 0
                                    || session.target != chain.getArg(plan.viewAt)) return chain.proceed();
                            session.recordDepth++;
                            Object result;
                            try { result = chain.proceed(); }
                            finally { session.recordDepth--; }
                            List<Object> incoming = chain.getArgs();
                            int at = session.calls.size();
                            HmcMaterialCall old = at < recipe.size() ? recipe.get(at) : null;
                            HmcMaterialCall call = old != null && old.matches(plan, incoming)
                                    ? old : new HmcMaterialCall(plan, incoming);
                            session.calls.add(call);
                            if (method.getName().equals("applyElementViewBlend")) {
                                if (method.getParameterCount() == 5) {
                                    int[] next = (int[]) chain.getArg(3);
                                    if (!Arrays.equals(colors, next)) { colors = next.clone(); generation++; }
                                } else if (method.getParameterCount() == 8) {
                                    int c1 = (int) chain.getArg(3), m1 = (int) chain.getArg(4);
                                    int c2 = (int) chain.getArg(5), m2 = (int) chain.getArg(6);
                                    int count = c2 == -1 || m2 == -1 ? 2 : 4;
                                    if (colors.length != count || colors[0] != c1 || colors[1] != m1
                                            || count == 4 && (colors[2] != c2 || colors[3] != m2)) {
                                        colors = count == 2 ? new int[] {c1, m1} : new int[] {c1, m1, c2, m2};
                                        generation++;
                                    }
                                }
                            } else if (method.equals(glass)) {
                                float[] next = (float[]) chain.getArg(1);
                                if (!Arrays.equals(params, next)) { params = next.clone(); generation++; }
                            }
                            return result;
                        });
            }
        }
    }

    private static int id(Resources res, String type, String name) {
        int value = res.getIdentifier(name, type, "com.android.systemui");
        if (value == 0) throw new IllegalStateException("Missing SystemUI resource: " + name);
        return value;
    }
}
