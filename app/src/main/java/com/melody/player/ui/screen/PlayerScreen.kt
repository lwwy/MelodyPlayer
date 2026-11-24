@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.melody.player.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.melody.player.model.LyricLine
import com.melody.player.model.Song
import com.melody.player.ui.AlbumArt
import com.melody.player.ui.ExpressivePlayPauseButton

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private data class LyricsSnapshot(val songId: Long, val lines: List<LyricLine>)

/**
 * 封面切换： expressive 形变——新封面轻微放大淡入，旧封面同步淡出。
 * 无阴影、无 1.08 冗余放大等历史 workaround，全部推倒重来。
 */
private fun AnimatedContentTransitionScope<Song>.coverSwitchSpec(): ContentTransform {
    val spec = tween<Float>(durationMillis = 340, easing = FastOutSlowInEasing)
    return (fadeIn(animationSpec = spec) + scaleIn(initialScale = 0.94f, animationSpec = spec)) togetherWith
        (fadeOut(animationSpec = spec) + scaleOut(targetScale = 1.04f, animationSpec = spec))
}

/** 歌曲信息切换：淡入淡出 + 轻微缩放。 */
private fun AnimatedContentTransitionScope<Song>.infoSwitchSpec(): ContentTransform {
    val spec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    return (fadeIn(animationSpec = spec) + scaleIn(initialScale = 0.96f, animationSpec = spec)) togetherWith
        fadeOut(animationSpec = spec)
}

/** 歌词整块溶解。 */
private fun AnimatedContentTransitionScope<LyricsSnapshot>.lyricsSwitchSpec(): ContentTransform {
    val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    return fadeIn(animationSpec = spec) togetherWith fadeOut(animationSpec = spec)
}

/**
 * Expressive 波形进度条：播放时带波浪律动，暂停时平直；
 * 点击定位与拖动 seek 均保留（暂停时也可拖动，松手后 seekTo 更新并续播）。
 */
@Composable
private fun SlimSeekBar(
    fraction: Float,
    isPlaying: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(30.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.coerceAtLeast(1)
                    onScrub((offset.x / w).coerceIn(0f, 1f))
                    onScrubFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.coerceAtLeast(1)
                        onScrub((offset.x / w).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.coerceAtLeast(1)
                        onScrub((change.position.x / w).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onScrubFinished() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LinearWavyProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceVariant,
            amplitude = { if (isPlaying) 0.6f else 0f }
        )
    }
}

@Composable
fun PlayerScreen(
    song: Song,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    repeatMode: Int,
    shuffle: Boolean,
    lyrics: List<LyricLine> = emptyList(),
    lyricsSongId: Long = -1L,
    navDirection: Int = 1,
    coverColor: Int? = null,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // 动态背景顶部色：封面主色平滑过渡，无封面时回退 primaryContainer
    val targetTopColor = coverColor?.let { Color(it) } ?: colorScheme.primaryContainer
    val animatedTopColor by animateColorAsState(
        targetValue = targetTopColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "backdrop-color"
    )
    val backdrop = Brush.verticalGradient(
        colors = listOf(animatedTopColor, colorScheme.surface)
    )

    val lyricsSnapshot = remember(lyricsSongId, lyrics) { LyricsSnapshot(lyricsSongId, lyrics) }

    var scrubFraction by remember { mutableFloatStateOf(-1f) }
    val effectiveDuration = durationMs.coerceAtLeast(1L)
    val progressFraction = (progressMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    val displayFraction = if (scrubFraction >= 0f) scrubFraction else progressFraction

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：收起按钮 + 大号"正在播放"标题（expressive emphasized）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shapes = IconButtonDefaults.shapes(
                        shape = RoundedCornerShape(16.dp),
                        pressedShape = CircleShape
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起播放页",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = colorScheme.onSurface
                    )
                }
                Spacer(Modifier.size(48.dp))
            }

            // 封面：大圆角 32dp，无阴影，外层容器统一裁剪
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = { coverSwitchSpec() },
                        label = "album-art-switch"
                    ) { targetSong ->
                        AlbumArt(
                            uri = targetSong.albumArtUri,
                            contentDescription = "专辑封面",
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp)
                        )
                    }
                }
            }

            // 歌曲信息：标题 + 歌手两行，去掉专辑名，留白更舒展
            AnimatedContent(
                targetState = song,
                transitionSpec = { infoSwitchSpec() },
                label = "song-info-switch"
            ) { infoSong ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        infoSong.title,
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        infoSong.artist,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 歌词：整块溶解，当前行高亮放大
            AnimatedContent(
                targetState = lyricsSnapshot,
                transitionSpec = { lyricsSwitchSpec() },
                label = "lyrics-switch"
            ) { snap ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                    ) {
                        LyricsView(
                            lyrics = snap.lines,
                            progressMs = progressMs,
                            onLineClick = onSeek,
                            compact = true,
                            songId = snap.songId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 进度条 + 时间
            SlimSeekBar(
                fraction = displayFraction,
                isPlaying = isPlaying,
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    if (scrubFraction >= 0f) onSeek((scrubFraction * effectiveDuration).toLong())
                    scrubFraction = -1f
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(progressMs), style = MaterialTheme.typography.labelMediumEmphasized)
                Spacer(Modifier.weight(1f))
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelMediumEmphasized)
            }

            Spacer(Modifier.height(8.dp))

            // 控制区：功能键带容器底色，播放键 84dp expressive 形变
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                FilledTonalIconToggleButton(
                    checked = shuffle,
                    onCheckedChange = { onToggleShuffle() },
                    modifier = Modifier.size(48.dp),
                    shapes = IconButtonDefaults.toggleableShapes(
                        checkedShape = RoundedCornerShape(16.dp)
                    )
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "随机播放")
                }
                FilledTonalIconButton(
                    onClick = onPrev,
                    modifier = Modifier.size(54.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = RoundedCornerShape(20.dp),
                        pressedShape = CircleShape
                    )
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一曲",
                        modifier = Modifier.size(36.dp)
                    )
                }
                ExpressivePlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlay
                )
                FilledTonalIconButton(
                    onClick = onNext,
                    modifier = Modifier.size(54.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = RoundedCornerShape(20.dp),
                        pressedShape = CircleShape
                    )
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一曲",
                        modifier = Modifier.size(36.dp)
                    )
                }
                FilledTonalIconToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { onToggleRepeat() },
                    modifier = Modifier.size(48.dp),
                    shapes = IconButtonDefaults.toggleableShapes(
                        checkedShape = RoundedCornerShape(16.dp)
                    )
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = "循环模式"
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
