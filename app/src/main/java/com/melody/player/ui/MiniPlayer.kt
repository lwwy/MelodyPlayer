package com.melody.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.melody.player.model.Song

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit
) {
    // 缩小版悬浮卡片：约 58dp 高、封面 42dp、圆角 18dp。
    // 实色 primaryContainer 背景（与列表「正在播放」行同款主题浅蓝，随壁纸动态变化），
    // 文字/图标用 onPrimaryContainer 系列自动适配明暗。移除 haze 高斯模糊——
    // 它既稀释了主题色（蓝色被冲淡成灰白），又在卡片四周形成散开的光晕。
    val shape = RoundedCornerShape(18.dp)
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(4.dp, shape, spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(shape)
            .background(container)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .clickable { onExpand() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            uri = song.albumArtUri,
            contentDescription = "专辑封面",
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(9.dp)
        )
        Spacer(Modifier.width(10.dp))
        // 进度条位于封面与右侧播放键之间的文字区，作为第三行
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = onContainer,
                trackColor = onContainer.copy(alpha = 0.18f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = onContainer
            )
        }
    }
}
