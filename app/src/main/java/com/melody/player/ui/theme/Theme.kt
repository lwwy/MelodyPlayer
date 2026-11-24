package com.melody.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive 主题：
 * - Android 12+ 默认跟随系统取色（Monet 动态配色）；
 * - 低版本回退到 M3 官方 baseline 明暗配色，不再手写固定品牌色。
 * - motionScheme = expressive：全局启用 Expressive 弹簧物理动效 token（形变、回弹、强调）。
 * - shapes / typography 使用 M3 1.5 Expressive 默认值（含 emphasized 字重变体与新形状档位）。
 * - edge-to-edge 状态栏/导航栏由 MainActivity 的 enableEdgeToEdge() 统一处理。
 */
@Composable
fun MelodyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content
    )
}
