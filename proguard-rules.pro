-keepattributes LineNumberTable,SourceFile
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Keep the unified annotation so reflective provider scanners can read it at runtime.
-keep @interface uk.kagurach.libhook.common.Hook
-keep enum uk.kagurach.libhook.common.HookPhase { *; }

# Deprecated legacy Xposed fallback entry points and callback types.
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * extends de.robv.android.xposed.XC_MethodHook { *; }

# Modern libXposed module entries are discovered through META-INF/xposed/java_init.list.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Reflective provider declarations, their no-argument receivers, and Kotlin object receivers.
# The provider scanner supports private members, so retain their names and access flags too.
-keepclasseswithmembers,allowoptimization class * {
    @uk.kagurach.libhook.common.Hook <methods>;
    <init>();
}
-keepclassmembers class * {
    public static final ** INSTANCE;
}
