-keep class org.matrix.chromext.MainHook {
 <init>();
}
-keepattributes SourceFile,LineNumberTable

# Byte Buddy generates classes at runtime and resolves members and annotations
# reflectively. Obfuscation is not supported by Byte Buddy.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep class net.bytebuddy.** { *; }
# The anonymous interceptors are only invoked reflectively by MethodDelegation.
-keep class org.matrix.chromext.utils.ChromeXtModuleContext$* { *; }

# AndroidClassLoadingStrategy resolves these dalvik-dx members by name.
-keep class com.android.dx.command.dexer.DxContext {
    <init>();
}
-keep class com.android.dx.dex.cf.CfTranslator {
    public static com.android.dx.dex.file.ClassDefItem translate(...);
}
-keep class com.android.dx.dex.DexOptions {
    public int minSdkVersion;
}

# Room instantiates the generated database implementation by name.
-keep class org.matrix.chromext.db.ChromeXtDatabase_Impl {
    <init>();
}

# Parasitics injects module activities into the host process and creates them
# with Class.newInstance(), so keep every Activity subclass, its no-arg
# constructor and lifecycle methods instead of listing individual classes.
-keep class * extends android.app.Activity { *; }

# Optional/compile-time-only types referenced by Byte Buddy and dalvik-dx.
-dontwarn net.bytebuddy.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn javax.annotation.**
