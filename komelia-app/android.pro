-dontobfuscate

-dontwarn java.sql.JDBCType
-dontwarn org.jboss.vfs.**
-dontwarn org.osgi.framework.**
-dontwarn org.postgresql.util.PGobject
-dontwarn software.amazon.awssdk.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.codahale.metrics.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.tukaani.xz.**

-keep class org.flywaydb.core.internal.logging.slf4j.** { *; }
-keep class org.sqlite.** { *; }
-keep class ch.qos.logback.classic.android.** { *; }
-keep class ch.qos.logback.classic.pattern.** { *; }
-keep class io.github.snd_r.komelia.** { *; }
-keep class snd.komelia.** { *; }

# ONNX Runtime (onnxruntime-android AAR). The native libonnxruntime4j_jni.so
# constructs ai.onnxruntime.* objects via JNI NewObject — e.g. TensorInfo's
# (long[], String[], int) constructor. R8 sees no Java caller and strips those
# members, so inference dies with NoSuchMethodError and the speech-bubble
# detector (invert bubbles + webtoon smart scroll, which share it) crashes.
# Keep the whole package incl. constructors/fields touched from native.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**