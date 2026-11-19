# TensorFlow Lite loads its native code through JNI and reflects on these classes; shrinking them breaks detection at runtime.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
