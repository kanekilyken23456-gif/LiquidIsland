# Настройки хранятся через kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.akira.island.data.**$$serializer { *; }
-keepclassmembers class com.akira.island.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.akira.island.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# LSPosed module: loaded by reflection from assets/xposed_init inside the System UI process, so the
# class names must survive shrinking/renaming. The Xposed API itself is compile-only (provided by LSPosed).
-keep class com.akira.island.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
