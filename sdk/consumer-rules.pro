-keep class com.helpers.** { *; }

# Keep StringConcatFactory (needed for string concatenation)
-keep class java.lang.invoke.StringConcatFactory {
    *;
}
-keepclassmembers class java.lang.invoke.StringConcatFactory {
    public static java.lang.invoke.CallSite makeConcat(java.lang.invoke.MethodHandles$Lookup, java.lang.String, java.lang.invoke.MethodType, java.lang.String, java.lang.Object[]);
    public static java.lang.invoke.CallSite makeConcatWithConstants(java.lang.invoke.MethodHandles$Lookup, java.lang.String, java.lang.invoke.MethodType, java.lang.String, java.lang.Object[]);
}
-dontwarn java.lang.invoke.StringConcatFactory