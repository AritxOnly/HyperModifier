-dontwarn io.github.libxposed.annotation.**

# Room 2.6.1 creates generated database implementations through reflection.
# Explicitly retain their no-arg constructors for R8 full mode (WorkManager startup).
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}

-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
