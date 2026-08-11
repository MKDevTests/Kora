-dontobfuscate

# NOTE: -dontoptimize used to sit here, blamed for a Bitmap.recycle()
# double-free SIGSEGV seen on KoraR8. That build was `isDebuggable = true`,
# which makes AGP disable R8's optimization pass anyway — so the crash happened
# with optimization already OFF and the flag never fixed anything, while costing
# dex 25 MB -> 52 MB. Removed, and KoraR8 is now non-debuggable so it actually
# exercises the optimized code. If the teardown crash comes back on a
# non-debuggable optimized build, put it back and reopen the race instead.

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
# logback is configured entirely from assets/logback.xml. Joran instantiates
# every appender, rolling policy, triggering policy and encoder reflectively,
# so R8 sees no Java reference to any of them and strips them — silently, since
# logback's own failure goes to System.out and the app keeps running without
# file logs. Only the LogcatAppender and the pattern classes were kept, which
# is why release builds since R8 was enabled wrote nothing to komelia.log:
# RollingFileAppender and AsyncAppender were both gone, and even
# PatternLayoutEncoder had lost its no-arg constructor.
# Keeping the whole tree rather than naming classes one by one: the XML can
# reference any of them, and logback-android is small next to a 25 MB dex.
-keep class ch.qos.logback.** { *; }
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