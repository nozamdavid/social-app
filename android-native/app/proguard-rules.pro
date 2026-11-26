-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# kotlinx.serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class am.noz.caelum.**$$serializer { *; }
-keepclassmembers class am.noz.caelum.** {
    *** Companion;
}
-keepclasseswithmembers class am.noz.caelum.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase {
    void <init>();
}
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Jetpack Glance widgets (ActionCallback reflection instantiation)
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# Media3 / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ML Kit optional GenAI
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
