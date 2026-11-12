package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class AuthenticatorGridLifecycleTest {
    public static class Manager {
        int anchor = 7;
        public int first() { return anchor; }
        public void scroll(int position) { anchor = position; }
    }
    public static class Grid extends Manager {
        public int span = 1;
        public Grid(Context context, AttributeSet attrs, int style, int res) {}
    }
    public static class Divider {}
    public static class ListView extends FrameLayout {
        public Manager manager = new Manager();
        public ArrayList<Object> decorations = new ArrayList<>();
        boolean fail;
        public ListView(Context context) { super(context); }
        public void manager(Manager next) {
            manager = next;
            if (fail && next instanceof Grid) throw new IllegalStateException("partial failure");
        }
        public void add(Object item) { decorations.add(item); }
        public void remove(Object item) { decorations.remove(item); }
    }
    private static void binding(String name, Object value) throws Exception {
        Field field = AuthenticatorGridHooks.class.getDeclaredField(name);
        field.setAccessible(true); field.set(null, value);
    }
    @Before public void bindFixture() throws Exception {
        binding("managerField", ListView.class.getField("manager"));
        binding("spanField", Grid.class.getField("span"));
        binding("gridConstructor", Grid.class.getConstructor(Context.class, AttributeSet.class, int.class, int.class));
        binding("setManager", ListView.class.getMethod("manager", Manager.class));
        binding("firstVisible", Manager.class.getMethod("first"));
        binding("scrollTo", Manager.class.getMethod("scroll", int.class));
        binding("decorations", ListView.class.getField("decorations"));
        binding("dividerClass", Divider.class);
        binding("addDecoration", ListView.class.getMethod("add", Object.class));
        binding("removeDecoration", ListView.class.getMethod("remove", Object.class));
        binding("enabled", true);
    }
    private Runnable state(ListView list) throws Exception {
        Constructor<?> constructor = Class.forName(AuthenticatorGridHooks.class.getName() + "$State")
                .getDeclaredConstructor(ViewGroup.class);
        constructor.setAccessible(true);
        return (Runnable) constructor.newInstance(list);
    }
    private ListView list(int dp) {
        ListView list = new ListView(RuntimeEnvironment.getApplication());
        width(list, dp);
        return list;
    }
    private void width(ListView list, int dp) {
        list.layout(0, 0, Math.round(dp * list.getResources().getDisplayMetrics().density), 900);
    }
    @Test public void switchesColumnsPreservesAnchorAndRestoresNativeObjects() throws Exception {
        ListView list = list(700);
        Manager original = list.manager;
        Divider divider = new Divider(); Object touchHelper = new Object();
        list.decorations.add(divider); list.decorations.add(touchHelper);
        Runnable state = state(list);
        state.run();
        assertEquals(2, ((Grid) list.manager).span);
        assertEquals(7, list.manager.anchor);
        assertFalse(list.decorations.contains(divider));
        assertTrue(list.decorations.contains(touchHelper));
        Manager same = list.manager;
        state.run(); assertSame(same, list.manager);
        width(list, 900); state.run();
        assertEquals(3, ((Grid) list.manager).span);
        width(list, 400); state.run();
        assertSame(original, list.manager);
        assertTrue(list.decorations.contains(divider));
        assertEquals(2, list.decorations.size());
        assertSame(divider, list.decorations.get(0));
        assertSame(touchHelper, list.decorations.get(1));
        width(list, 900); state.run();
        binding("enabled", false); state.run();
        assertSame(original, list.manager);
    }
    @Test public void respectsReplacementByAnotherModuleOnDisable() throws Exception {
        ListView list = list(900); Runnable state = state(list); state.run();
        Manager other = new Manager(); list.manager = other;
        binding("enabled", false); state.run();
        assertSame(other, list.manager);
    }
    @Test public void rollsBackPartialManagerFailureAndRestoresDivider() throws Exception {
        ListView list = list(900);
        Manager original = list.manager;
        Divider divider = new Divider(); list.decorations.add(divider);
        list.fail = true;
        Runnable state = state(list); state.run();
        assertSame(original, list.manager);
        assertTrue(list.decorations.contains(divider));
        state.run(); assertSame(original, list.manager);
    }

}
