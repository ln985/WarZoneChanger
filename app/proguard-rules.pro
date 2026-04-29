# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.warzone.changer.model.** { *; }
-keep class com.github.megatronking.netbare.** { *; }
-dontwarn com.github.megatronking.netbare.**