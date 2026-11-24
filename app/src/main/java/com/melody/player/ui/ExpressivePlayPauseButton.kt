@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.melody.player.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive 播放/暂停按钮：84dp 主按钮，
 * 静止时 expressive 圆角方形，按下时形变为圆形（shape morph）——M3 Expressive 标志性反馈。
 */
@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(
            shape = RoundedCornerShape(28.dp),
            pressedShape = CircleShape
        ),
        modifier = modifier.size(84.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(36.dp)
        )
    }
}
