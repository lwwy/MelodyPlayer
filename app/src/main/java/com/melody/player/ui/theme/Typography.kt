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
 * Material 3 Expressive 排版：用 Typography(fontFamily) 构造一次性把 Roboto 应用到
 * 全部 type scale —— 包含 Expressive 新增的 emphasized 变体（display/headline/title/body/
 * label *Emphasized），字号、字重、字距与 emphasized 粗细对比全部保持官方规范。
 */
val Typography = Typography(fontFamily = RobotoFontFamily)
