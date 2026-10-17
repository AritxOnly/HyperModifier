package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalWidgetCatalogJobTest {
    public interface Key {}
    public interface Element {}
    public interface Context { Element get(Key key); }
    public interface FirstJob extends Element {
        boolean isActive();
        class ObfuscatedKey implements Key { public static final ObfuscatedKey x = new ObfuscatedKey(); }
    }
    public interface OtherReleaseJob extends Element {
        boolean isActive();
        class DifferentKey implements Key { public static final DifferentKey y = new DifferentKey(); }
    }
    public interface WrongType extends Element { int isActive(); }
    public interface AmbiguousJob extends Element {
        boolean isActive();
        class First implements Key { public static final First x = new First(); }
        class Second implements Key { public static final Second y = new Second(); }
    }
    @Test public void changedJobAndKeyNamesKeepCancellationChecks() throws Exception {
        LocalWidgetCatalogJob first = new LocalWidgetCatalogJob(Context.class, FirstJob.class);
        LocalWidgetCatalogJob second = new LocalWidgetCatalogJob(Context.class, OtherReleaseJob.class);
        assertSame(FirstJob.ObfuscatedKey.x, first.key);
        assertSame(OtherReleaseJob.DifferentKey.y, second.key);
        OtherReleaseJob cancelled = () -> false;
        Context context = key -> key == second.key ? cancelled : null;
        assertSame(cancelled, second.contextGet.invoke(context, second.key));
        assertEquals(false, second.active.invoke(cancelled));
    }
    @Test public void incompatibleAndAmbiguousContractsAreRejected() {
        for (Class<?> type : new Class<?>[]{Object.class, WrongType.class, AmbiguousJob.class}) {
            try { new LocalWidgetCatalogJob(Context.class, type); fail(type.getName()); }
            catch (ReflectiveOperationException expected) { /* no unsafe fallback */ }
        }
    }
}
