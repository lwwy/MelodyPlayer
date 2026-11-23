package com.melody.player.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melody.player.model.LyricLine

/**
 * 滚动歌词视图：根据播放进度高亮当前行，并自动把当前行滚动到中间位置。
 * 点击某行可跳转到该行时间戳（通过 [onLineClick] 回传）。
 *
 * [compact] 为 true 时用于播放页「封面与播放键之间」的小歌词区：字号与留白收紧，
 * 当前行仍居中高亮；为 false 时用于全屏歌词，留白更大。
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    progressMs: Long,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    songId: Long = -1L
) {
    val listState = rememberLazyListState()
    // 记录上一次的歌曲，用于区分「切歌」与「同曲内进度推进」
    val lastSongId = remember { mutableLongStateOf(songId) }

    val edgeSpace = if (compact) 40.dp else 120.dp
    val scrollOffset = if (compact) 0 else 0
    val currentFont = if (compact) 18.sp else 20.sp
    val otherFont = if (compact) 15.sp else 17.sp
    val verticalPad = if (compact) 6.dp else 10.dp

    // 当前行：最后一个 timeMs <= progressMs 的行
    var currentIndex = -1
    for (i in lyrics.indices) {
        if (lyrics[i].timeMs <= progressMs) currentIndex = i else break
    }

    LaunchedEffect(currentIndex, songId) {
        if (currentIndex >= 0) {
            if (lastSongId.longValue != songId) {
                // 切歌：瞬时跳到新歌当前行，避免从旧歌位置长距离倒滚
                lastSongId.longValue = songId
                listState.scrollToItem(index = currentIndex, scrollOffset = scrollOffset)
            } else {
                // 同一首歌内：平滑滚动到当前行，负 offset 让当前行偏上便于看到下一句
                listState.animateScrollToItem(index = currentIndex, scrollOffset = scrollOffset)
            }
        }
    }

    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(edgeSpace)) }
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentIndex
            val alpha by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.4f,
                label = "lyric-alpha"
            )
            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.95f,
                label = "lyric-scale"
            )
            Text(
                text = line.text.ifBlank { "♪" },
                textAlign = TextAlign.Center,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontSize = if (isCurrent) currentFont else otherFont,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                lineHeight = if (compact) 24.sp else 28.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = verticalPad)
                    .alpha(alpha)
                    .scale(scale)
                    .clickable { onLineClick(line.timeMs) }
            )
        }
        item { Spacer(Modifier.height(edgeSpace)) }
    }
}
