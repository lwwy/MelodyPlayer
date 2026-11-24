@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.melody.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.melody.player.model.Song

/**
 * Material 3 Expressive docked 迷你播放器：
 * 大圆角 primaryContainer 悬浮卡片，封面放大到 56dp，标题 emphasized，
 * 播放键带容器底色并在按下时形变，点击卡片展开全屏播放页。
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(shape)
            .background(container)
            .clickable { onExpand() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            uri = song.albumArtUri,
            contentDescription = "专辑封面",
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = onContainer,
                trackColor = onContainer.copy(alpha = 0.2f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(48.dp),
            shapes = IconButtonDefaults.shapes(
                shape = RoundedCornerShape(16.dp),
                pressedShape = CircleShape
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
