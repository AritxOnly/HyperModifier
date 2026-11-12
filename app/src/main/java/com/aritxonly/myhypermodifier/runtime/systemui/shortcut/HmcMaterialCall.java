package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** HMC Recorded(method,args,viewAt), with View/Context slots kept as placeholders. */
final class HmcMaterialCall {
    static final class Plan {
        final Method method;
        final int viewAt;
        final boolean[] contextAt;
        Plan(Method method) {
            this.method = method;
            Class<?>[] types = method.getParameterTypes();
            contextAt = new boolean[types.length];
            int target = -1;
            for (int i = 0; i < types.length; i++) {
                if (View.class.isAssignableFrom(types[i])) target = i;
                contextAt[i] = Context.class.isAssignableFrom(types[i]);
            }
            viewAt = target;
        }
    }
    final Plan plan;
    final Object[] arguments;
    HmcMaterialCall(Plan plan, List<Object> incoming) {
        this.plan = plan;
        arguments = new Object[incoming.size()];
        for (int i = 0; i < arguments.length; i++) {
            if (i != plan.viewAt && !plan.contextAt[i]) arguments[i] = copyArray(incoming.get(i));
        }
    }
    boolean matches(Plan next, List<Object> incoming) {
        if (plan != next || arguments.length != incoming.size()) return false;
        for (int i = 0; i < arguments.length; i++) {
            if (i == plan.viewAt || plan.contextAt[i]) continue;
            Object left = arguments[i], right = incoming.get(i);
            boolean same = left instanceof int[] && right instanceof int[] ? Arrays.equals((int[]) left, (int[]) right)
                    : left instanceof float[] && right instanceof float[] ? Arrays.equals((float[]) left, (float[]) right)
                    : java.util.Objects.equals(left, right);
            if (!same) return false;
        }
        return true;
    }
    void replay(Context context, View view) throws ReflectiveOperationException {
        Object[] args = new Object[arguments.length];
        for (int i = 0; i < args.length; i++) args[i] = i == plan.viewAt ? view
                : plan.contextAt[i] ? context : copyArray(arguments[i]);
        plan.method.invoke(null, args);
    }
    private static Object copyArray(Object value) {
        return value instanceof int[] ? ((int[]) value).clone()
                : value instanceof float[] ? ((float[]) value).clone() : value;
    }
}
