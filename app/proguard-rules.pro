# kotlinx.serialization keeps generated serializers on the model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.antidoomscroller.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.antidoomscroller.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.antidoomscroller.core.**$$serializer { *; }

# Services are referenced from the manifest only.
-keep class com.antidoomscroller.service.** { *; }
-keep class com.antidoomscroller.vpn.** { *; }
