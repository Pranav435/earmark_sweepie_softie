# Room and ML Kit ship their own consumer rules. Keep line numbers so DebugLog stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit finds its components by reflection from manifest metadata; without these R8 strips them
# and SmartReply.getClient() fails in release builds only.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_smartreply** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.common.internal.MlKitComponentDiscoveryService { *; }
