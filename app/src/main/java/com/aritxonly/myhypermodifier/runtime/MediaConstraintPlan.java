package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.util.Xml;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.xmlpull.v1.XmlPullParser;

/** Bounded, resource/configuration-scoped plans. Never retains a Context, View or Resources. */
final class MediaConstraintPlan {
    private static final Map<Resources, ResourcePlans> CACHE = new WeakHashMap<>();
    private static final Plan INVALID = new Plan(new Target[0]);
    private static final Map<String, String> FIELDS = new HashMap<>();
    static {
        String[][] fields = {
            {"layout_width", "mWidth"}, {"layout_height", "mHeight"},
            {"layout_marginStart", "startMargin"}, {"layout_marginEnd", "endMargin"},
            {"layout_marginLeft", "leftMargin"}, {"layout_marginRight", "rightMargin"},
            {"layout_marginTop", "topMargin"}, {"layout_marginBottom", "bottomMargin"},
            {"layout_goneMarginStart", "goneStartMargin"}, {"layout_goneMarginEnd", "goneEndMargin"},
            {"layout_goneMarginTop", "goneTopMargin"}, {"layout_goneMarginBottom", "goneBottomMargin"},
            {"layout_constraintHorizontal_bias", "horizontalBias"},
            {"layout_constraintVertical_bias", "verticalBias"},
            {"layout_constraintHorizontal_chainStyle", "horizontalChainStyle"},
            {"layout_constraintVertical_chainStyle", "verticalChainStyle"},
            {"layout_constraintDimensionRatio", "dimensionRatio"},
            {"layout_constraintWidth_percent", "matchConstraintPercentWidth"},
            {"layout_constraintHeight_percent", "matchConstraintPercentHeight"},
            {"layout_constraintGuide_begin", "guideBegin"}, {"layout_constraintGuide_end", "guideEnd"},
            {"layout_constraintGuide_percent", "guidePercent"}
        };
        for (String[] field : fields) FIELDS.put(field[0], field[1]);
        for (String side : new String[]{"Start", "End", "Left", "Right", "Top", "Bottom", "Baseline"}) {
            for (String other : new String[]{"Start", "End", "Left", "Right", "Top", "Bottom", "Baseline"}) {
                FIELDS.put("layout_constraint" + side + "_to" + other + "Of",
                        Character.toLowerCase(side.charAt(0)) + side.substring(1) + "To" + other);
            }
        }
    }

    private static final ClassValue<Map<String, Field>> PUBLIC_FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new HashMap<>();
            for (Field field : type.getFields()) fields.put(field.getName(), field);
            return fields;
        }
    };
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) { return new Access(type); }
    };

    static boolean apply(Context context, Object set, String source, boolean island) {
        if (set == null || source == null || source.isBlank() || source.length() > 64 * 1024) return false;
        Plan plan = resolve(context, source, island);
        if (plan == INVALID) return false;
        Access access = ACCESS.get(set.getClass());
        if (access.getConstraint == null) return false;
        boolean changed = false;
        try {
            for (Target target : plan.targets) {
                Object constraint = access.getConstraint.invoke(set, target.id);
                if (constraint == null) continue;
                Object layout = get(constraint, "layout");
                for (Assignment value : target.values) put(layout, value.field, value.value);
                if (target.visibility >= 0) {
                    Object properties = get(constraint, "propertySet");
                    put(properties, "visibility", target.visibility);
                    put(properties, "mVisibilityMode", 0);
                }
                changed = true;
            }
            return changed;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Plan resolve(Context context, String source, boolean island) {
        if (source == null || source.isBlank() || source.length() > 64 * 1024) return INVALID;
        Plan plan;
        Resources resources = context.getResources();
        synchronized (CACHE) {
            ResourcePlans owner = CACHE.get(resources);
            if (owner == null || !owner.configuration.equals(resources.getConfiguration())
                    || !owner.packageName.equals(context.getPackageName())) {
                owner = new ResourcePlans(resources.getConfiguration(), context.getPackageName());
                CACHE.put(resources, owner);
            }
            Map<String, Plan> plans = island ? owner.island : owner.normal;
            plan = plans.get(source);
            if (plan == null) {
                plan = compile(context, source, island);
                if (plans.size() == 4) plans.remove(plans.keySet().iterator().next());
                plans.put(source, plan);
            }
        }
        return plan;
    }

    static boolean hidesView(Context context, String source, boolean island, int id) {
        for (Target target : resolve(context, source, island).targets) {
            if (target.id == id) return target.visibility == android.view.View.GONE;
        }
        return false;
    }

    /** Respect native AOD/data visibility: reassert only explicit GONE overrides. */
    static void hideSuppressedViews(android.view.View root, String source, boolean island) {
        for (Target target : resolve(root.getContext(), source, island).targets) {
            if (target.visibility != android.view.View.GONE) continue;
            android.view.View view = root.findViewById(target.id);
            if (view != null && view.getVisibility() != android.view.View.GONE) view.setVisibility(android.view.View.GONE);
        }
    }

    private static Plan compile(Context context, String source, boolean island) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(source));
            List<Target> targets = new ArrayList<>();
            int count = 0;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG || !"Constraint".equals(parser.getName())) continue;
                if (++count > 48) throw new IllegalArgumentException("Too many constraints");
                String name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "id");
                if (name == null) continue;
                if (island && ("media_bg".equals(name) || name.endsWith("/media_bg"))) name = "@id/media_bg_view";
                int id = MediaConstraintCustomizer.parseConstraintTarget(context, name);
                if (id <= 0) continue;
                List<Assignment> values = new ArrayList<>();
                int visibility = -1;
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    String attribute = parser.getAttributeName(i), value = parser.getAttributeValue(i);
                    if ("visibility".equals(attribute)) {
                        visibility = switch (value) { case "gone", "8" -> 8; case "invisible", "4" -> 4; case "visible", "0" -> 0; default -> -1; };
                        continue;
                    }
                    if ("layout_margin".equals(attribute)) {
                        int margin = MediaConstraintCustomizer.parseDimension(context, value);
                        for (String side : new String[]{"left", "right", "top", "bottom"}) values.add(new Assignment(side + "Margin", margin));
                        continue;
                    }
                    String field = FIELDS.get(attribute);
                    if (field == null) continue;
                    Object parsed;
                    if (attribute.endsWith("Of")) {
                        if (island && ("media_bg".equals(value) || value.endsWith("/media_bg"))) value = "@id/media_bg_view";
                        parsed = MediaConstraintCustomizer.parseConstraintTarget(context, value);
                    }
                    else if (attribute.endsWith("chainStyle")) parsed = MediaConstraintCustomizer.parseChainStyle(value);
                    else if (attribute.endsWith("Ratio")) parsed = value;
                    else if (attribute.endsWith("bias") || attribute.endsWith("percent")) parsed = Float.parseFloat(value);
                    else parsed = MediaConstraintCustomizer.parseDimension(context, value);
                    values.add(new Assignment(field, parsed));
                }
                targets.add(new Target(id, visibility, values.toArray(new Assignment[0])));
            }
            return new Plan(targets.toArray(new Target[0]));
        } catch (Exception error) {
            Log.w("MyHyperModifier", "Invalid media constraint plan; using stock layout", error);
            return INVALID;
        }
    }

    private static Object get(Object object, String name) throws IllegalAccessException {
        if (object == null) return null;
        Field field = PUBLIC_FIELDS.get(object.getClass()).get(name);
        return field == null ? null : field.get(object);
    }
    private static void put(Object object, String name, Object value) throws IllegalAccessException {
        if (object == null) return;
        Field field = PUBLIC_FIELDS.get(object.getClass()).get(name);
        if (field != null) field.set(object, value);
    }
    private static final class Access {
        final Method getConstraint;
        Access(Class<?> type) {
            Method method = null;
            try { method = type.getMethod("getConstraint", int.class); }
            catch (NoSuchMethodException ignored) { }
            getConstraint = method;
        }
    }
    private static final class ResourcePlans {
        final Configuration configuration;
        final String packageName;
        final Map<String, Plan> normal = new LinkedHashMap<>(), island = new LinkedHashMap<>();
        ResourcePlans(Configuration configuration, String packageName) {
            this.configuration = new Configuration(configuration);
            this.packageName = packageName;
        }
    }
    private record Assignment(String field, Object value) {}
    private record Target(int id, int visibility, Assignment[] values) {}
    private record Plan(Target[] targets) {}
}
