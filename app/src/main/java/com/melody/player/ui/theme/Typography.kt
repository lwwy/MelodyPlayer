package com.melody.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.melody.player.R

/**
 * 谷歌官方推荐字体 Roboto（Material Design 默认字体族）。
 * 打包 Regular / Medium / Bold 三档，保证英文与数字在各机型一致；中文由系统字体自动 fallback。
 */
val RobotoFontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

/**
 * 以 Material 3 官方 type scale 为基准，仅把字体族统一替换为 Roboto。
 * 这样字号/字重/字距全部保持官方规范，只是字体换成谷歌推荐的 Roboto。
 */
private val MaterialTypography = Typography()

val Typography = MaterialTypography.copy(
    displayLarge = MaterialTypography.displayLarge.copy(fontFamily = RobotoFontFamily),
    displayMedium = MaterialTypography.displayMedium.copy(fontFamily = RobotoFontFamily),
    displaySmall = MaterialTypography.displaySmall.copy(fontFamily = RobotoFontFamily),
    headlineLarge = MaterialTypography.headlineLarge.copy(fontFamily = RobotoFontFamily),
    headlineMedium = MaterialTypography.headlineMedium.copy(fontFamily = RobotoFontFamily),
    headlineSmall = MaterialTypography.headlineSmall.copy(fontFamily = RobotoFontFamily),
    titleLarge = MaterialTypography.titleLarge.copy(fontFamily = RobotoFontFamily),
    titleMedium = MaterialTypography.titleMedium.copy(fontFamily = RobotoFontFamily),
    titleSmall = MaterialTypography.titleSmall.copy(fontFamily = RobotoFontFamily),
    bodyLarge = MaterialTypography.bodyLarge.copy(fontFamily = RobotoFontFamily),
    bodyMedium = MaterialTypography.bodyMedium.copy(fontFamily = RobotoFontFamily),
    bodySmall = MaterialTypography.bodySmall.copy(fontFamily = RobotoFontFamily),
    labelLarge = MaterialTypography.labelLarge.copy(fontFamily = RobotoFontFamily),
    labelMedium = MaterialTypography.labelMedium.copy(fontFamily = RobotoFontFamily),
    labelSmall = MaterialTypography.labelSmall.copy(fontFamily = RobotoFontFamily)
)
