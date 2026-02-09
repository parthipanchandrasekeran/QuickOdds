# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============ General ============
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ============ Gson ============
# Keep all classes that use @SerializedName (Gson reflection)
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson internals
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# ============ App data models (Gson serialized) ============
-keep class com.quickodds.app.data.remote.dto.** { *; }
-keep class com.quickodds.app.data.remote.api.** { *; }

# ============ AI models (Gson serialized request/response) ============
-keep class com.quickodds.app.ai.api.** { *; }
-keep class com.quickodds.app.ai.model.** { *; }
-keep class com.quickodds.app.ai.SettlementResponse { *; }

# ============ Retrofit ============
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
