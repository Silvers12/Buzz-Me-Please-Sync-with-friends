# kotlinx.serialization ------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class fr.buzzme.** {
    *** Companion;
}
-keepclasseswithmembers class fr.buzzme.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class fr.buzzme.**$$serializer { *; }

# Firebase Realtime Database uses reflection to map POJOs -------------------
-keepclassmembers class fr.buzzme.net.online.** {
    *;
}
-keepattributes Signature
-dontwarn com.google.firebase.**
