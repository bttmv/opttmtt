# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.opengate.app.**$$serializer { *; }
-keepclassmembers class com.opengate.app.** { *** Companion; }
-keepclasseswithmembers class com.opengate.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.opengate.app.data.model.** { *; }

# --- supabase-kt / ktor ---
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.management.**
-dontwarn kotlinx.coroutines.debug.**
