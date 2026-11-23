# ============================================================
# Melody Player - R8 keep 规则
# ============================================================

# ---------- 通用：保留行号便于崩溃定位 ----------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod

# ---------- Kotlin ----------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---------- Media3 / ExoPlayer ----------
# MediaSessionService 由系统依据 manifest 反射实例化，必须保留
-keep class com.melody.player.playback.PlaybackService { *; }
# Media3 会话/控制器/媒体通知相关类保留，避免反射或 JNI 访问失败
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# ---------- Compose ----------
# Compose 编译器生成的代码依赖运行时注解，保留 runtime 类
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------- 协程 ----------
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# ---------- 应用自身 ----------
# data class（可能被反射/调试访问）与 BuildConfig 保留
-keep class com.melody.player.model.** { *; }
-keep class com.melody.player.BuildConfig { *; }

# ---------- native 方法 ----------
-keepclasseswithmembernames class * {
    native <methods>;
}
