package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HmcMaterialCallTest {
    public static void nativeCall(Context context, View view, int[] colors, float[] params, int mode) {
        colors[0] = 99;
        params[0] = 99;
    }
    @Test public void snapshotDoesNotRetainTargetsAndCopiesMutableArrays() throws Exception {
        Method method = getClass().getMethod("nativeCall", Context.class, View.class, int[].class, float[].class, int.class);
        HmcMaterialCall.Plan plan = new HmcMaterialCall.Plan(method);
        int[] colors = {1, 101};
        float[] params = {1, 2};
        List<Object> input = Arrays.asList(new Object(), new Object(), colors, params, 1);
        HmcMaterialCall call = new HmcMaterialCall(plan, input);
        assertNull(call.arguments[0]);
        assertNull(call.arguments[1]);
        assertTrue(call.matches(plan, input));
        colors[0] = 9;
        params[0] = 9;
        assertArrayEquals(new int[] {1, 101}, (int[]) call.arguments[2]);
        assertArrayEquals(new float[] {1, 2}, (float[]) call.arguments[3], 0);
        assertFalse(call.matches(plan, input));
        assertTrue(call.matches(plan, Arrays.asList(new Object(), new Object(), new int[] {1, 101}, new float[] {1, 2}, 1)));
    }
    @Test public void orderingAndScalarChangesCannotReuseAnotherCall() throws Exception {
        Method method = getClass().getMethod("nativeCall", Context.class, View.class, int[].class, float[].class, int.class);
        HmcMaterialCall.Plan first = new HmcMaterialCall.Plan(method);
        HmcMaterialCall.Plan second = new HmcMaterialCall.Plan(method);
        List<Object> args = Arrays.asList(null, null, new int[] {1}, new float[] {2}, 0);
        HmcMaterialCall call = new HmcMaterialCall(first, args);
        assertFalse(call.matches(second, args));
        assertFalse(call.matches(first, Arrays.asList(null, null, new int[] {1}, new float[] {2}, 1)));
    }
    @Test public void replayCannotLetNativeSettersMutateStoredRecipe() throws Exception {
        Method method = getClass().getMethod("nativeCall", Context.class, View.class, int[].class, float[].class, int.class);
        HmcMaterialCall.Plan plan = new HmcMaterialCall.Plan(method);
        HmcMaterialCall call = new HmcMaterialCall(plan, Arrays.asList(null, null, new int[] {1}, new float[] {2}, 0));
        call.replay(null, null);
        call.replay(null, null);
        assertArrayEquals(new int[] {1}, (int[]) call.arguments[2]);
        assertArrayEquals(new float[] {2}, (float[]) call.arguments[3], 0);
    }

}
