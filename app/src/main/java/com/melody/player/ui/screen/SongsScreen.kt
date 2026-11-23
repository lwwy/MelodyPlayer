package com.melody.player.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.melody.player.R
import com.melody.player.model.Song
import com.melody.player.ui.AlbumArt

@Composable
fun SongsScreen(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    isLoading: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    bottomContentPadding: Dp = 0.dp,
    onSongClick: (Song) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            songs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.empty_library), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.empty_library_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = bottomContentPadding)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            uri = song.albumArtUri,
            contentDescription = "专辑封面",
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 仅当前正在播放时显示动态均衡器；其余行右侧留空（时长已取消）
        if (isCurrent && isPlaying) {
            Spacer(Modifier.width(8.dp))
            AnimatedEqualizer(modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * 正在播放的动态均衡器：4 根竖条以不同周期错峰跳动，营造播放律动感。
 * 仅在「当前歌曲且正在播放」时渲染，暂停/非当前歌曲不显示动画。
 */
@Composable
private fun AnimatedEqualizer(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "equalizer")
    // 4 根柱子错峰跳动；缓动用 Compose 官方 FastOutSlowInEasing（M3 Standard 曲线）
    fun standard(duration: Int): androidx.compose.animation.core.InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(
            tween<Float>(durationMillis = duration, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        )
    val heights = listOf(
        transition.animateFloat(4f, 20f, standard(620), label = "eq1"),
        transition.animateFloat(10f, 24f, standard(480), label = "eq2"),
        transition.animateFloat(6f, 22f, standard(740), label = "eq3"),
        transition.animateFloat(12f, 20f, standard(540), label = "eq4")
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
    ) {
        heights.forEach { height ->
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
