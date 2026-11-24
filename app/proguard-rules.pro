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
# Media3 / Compose 库自带 consumer rules，无需整包 keep（整包保留会抵消 R8 压缩、增大包体）
-dontwarn androidx.media3.**

# ---------- Compose ----------
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
