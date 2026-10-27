# kotlinx.serialization
-keepattributes *Annotation*,InnerClasses,Signature
-keep,includedescriptorclasses class com.localvault.passwordbook.**$$serializer { *; }
-keepclassmembers class com.localvault.passwordbook.**
-keepclasseswithmembers class com.localvault.passwordbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}
