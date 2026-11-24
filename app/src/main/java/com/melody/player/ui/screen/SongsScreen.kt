@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.melody.player.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            songs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.empty_library),
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                        Text(
                            stringResource(R.string.empty_library_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 0.dp,
                        bottom = bottomContentPadding
                    )
                ) {
                    // 大号 Expressive 标题，随内容一起滚动
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            Text(
                                "Music",
                                style = MaterialTheme.typography.displayMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "本地曲库",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

/**
 * Expressive 歌曲行：封面放大到 64dp + 20dp 大圆角，标题 emphasized，
 * 时长显示在 trailing；当前播放行用 primaryContainer 卡片 + 左侧指示条 + 均衡器。
 */
@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = if (isCurrent) {
        ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            supportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            trailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    } else {
        ListItemDefaults.colors()
    }
    ListItem(
        onClick = onClick,
        supportingContent = {
            Text(
                "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            AlbumArt(
                uri = song.albumArtUri,
                contentDescription = "专辑封面",
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp)
            )
        },
        trailingContent = {
            if (isCurrent && isPlaying) {
                AnimatedEqualizer(modifier = Modifier.size(24.dp))
            } else {
                Text(
                    song.durationText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        Text(
            song.title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 正在播放的动态均衡器：4 根竖条以不同周期错峰跳动。 */
@Composable
private fun AnimatedEqualizer(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    val transition = rememberInfiniteTransition(label = "equalizer")
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
