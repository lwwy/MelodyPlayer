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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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

/** 歌词快照：把歌词与所属歌曲绑定，供 AnimatedContent 稳定持有新旧两份内容做整块溶解。 */
private data class LyricsSnapshot(val songId: Long, val lines: List<LyricLine>)

/**
 * 封面切歌动画：交叉溶解 + 同向推进缩放（Apple Music 风格）。
 * 新封面从略大(0.96)淡入到 1，旧封面从 1 放大到 1.04 淡出——两者同向放大，
 * 观感像同一张图推进，而非两张图错位。
 *
 * 光晕根因：旧封面 scaleOut(1.04) 叠加基础放大 = 1.12，始终比新封面（1.037→1.08）大；
 * 淡出末段旧图仍半透明，其放大后的边缘露在新封面之外，形成"四周光晕散开"。
 * 修复双保险：① 旧封面不再放大（scaleOut 目标 1.0，尺寸恒为 1.08）——根治露边；
 * ② 旧封面淡出加速到 150ms，来不及显形即消失。
 * 新封面的 scaleIn(0.96→1) 推进感、350ms 速率全部保持原样，观感不变。
 */
private fun AnimatedContentTransitionScope<Song>.coverSwitchSpec(): ContentTransform {
    val scale = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)
    return (fadeIn(animationSpec = scale) + scaleIn(initialScale = 0.96f, animationSpec = scale)) togetherWith
        (fadeOut(animationSpec = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 1.0f, animationSpec = scale))
}

/**
 * 歌曲信息切歌动画：淡入淡出 + 轻微垂直位移（新文字从下方 12dp 浮现、旧文字上移退出）。
 */
private fun AnimatedContentTransitionScope<Song>.infoSwitchSpec(): ContentTransform {
    val fade = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    return (fadeIn(animationSpec = fade) + slideInVertically(initialOffsetY = { 12 })) togetherWith
        (fadeOut(animationSpec = fade) + slideOutVertically(targetOffsetY = { -12 }))
}

/** 歌词整块溶解：切歌时旧歌词淡出、新歌词淡入，配合内部进度滚动。 */
private fun AnimatedContentTransitionScope<LyricsSnapshot>.lyricsSwitchSpec(): ContentTransform {
    val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    return fadeIn(animationSpec = spec) togetherWith fadeOut(animationSpec = spec)
}

/**
 * 精简进度条：细轨道（4dp）+ 小圆点（12dp），支持点击定位与拖动。
 * 暂停时同样可拖动：松手后由 ViewModel 的 seekTo 更新进度并续播。
 */
@Composable
private fun SlimSeekBar(
    fraction: Float,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val trackHeight = 4.dp
    val thumbSize = 12.dp
    BoxWithConstraints(
        modifier = modifier
            .height(20.dp)
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
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = maxWidth
        // 轨道底色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(colorScheme.surfaceVariant)
        )
        // 已播放部分
        Box(
            modifier = Modifier
                .width(trackWidth * fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(colorScheme.primary)
        )
        // 小圆点 thumb
        Box(
            modifier = Modifier
                .offset(x = (trackWidth * fraction - thumbSize / 2).coerceAtLeast(0.dp))
                .size(thumbSize)
                .clip(CircleShape)
                .background(colorScheme.primary)
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

    // 歌词快照：仅当歌词数据真正变化时才换对象，保证切歌过渡期新旧两层内容各自稳定
    val lyricsSnapshot = remember(lyricsSongId, lyrics) { LyricsSnapshot(lyricsSongId, lyrics) }

    // 拖动时展示本地值，松手才真正 seek
    var scrubFraction by remember { mutableFloatStateOf(-1f) }
    val effectiveDuration = durationMs.coerceAtLeast(1L)
    val progressFraction = (progressMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    val displayFraction = if (scrubFraction >= 0f) scrubFraction else progressFraction

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：收起按钮 + 标题（返回也可直接用系统返回键）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起播放页",
                        modifier = Modifier.size(30.dp)
                    )
                }
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(48.dp))
            }

            // 封面：阴影放在固定外层容器，不参与切歌动画（避免过渡期两份阴影叠加 +
            // 缩放拉伸导致四周闪光晕）；AnimatedContent 只在阴影框内切换图片本身。
            // targetState 传整个 Song，内容 lambda 用 it —— 保证新旧两层是不同的封面。
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        // 阴影从 14dp 减到 6dp 并压低 spotColor：此前 14dp 的扩散阴影
                        // 在封面底部尤其明显，看起来像一圈散开的光晕。
                        .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.20f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            // 基础放大 1.08：叠加 scaleIn(0.96) 后实际仍 ≥1，图片始终盖满容器，
                            // 不会在缩放动画中露出兜底色；多出的边缘由外层容器统一裁剪。
                            modifier = Modifier.fillMaxSize().scale(1.08f),
                            // 内层不裁圆角：避免 1.08 放大后内圆角(~26dp)与外层裁剪(24dp)不匹配，
                            // 在四角露出兜底色形成"光晕"。最终圆角完全由外层容器 clip 决定。
                            shape = RoundedCornerShape(0.dp)
                        )
                    }
                }
            }

            // 歌曲信息：淡入淡出 + 轻微垂直位移，与封面同步
            AnimatedContent(
                targetState = song,
                transitionSpec = { infoSwitchSpec() },
                label = "song-info-switch"
            ) { infoSong ->
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        infoSong.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        infoSong.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        infoSong.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 歌词：整块溶解（歌词与所属歌绑定成快照，新旧两层内容真正不同）
            AnimatedContent(
                targetState = lyricsSnapshot,
                transitionSpec = { lyricsSwitchSpec() },
                label = "lyrics-switch"
            ) { snap ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.layout.Box(
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

            Spacer(Modifier.height(10.dp))

            // 精简进度条：暂停时也可拖动，松手后由 seekTo 更新进度并续播
            SlimSeekBar(
                fraction = displayFraction,
                onScrub = { scrubFraction = it },
                onScrubFinished = {
                    if (scrubFraction >= 0f) onSeek((scrubFraction * effectiveDuration).toLong())
                    scrubFraction = -1f
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(progressMs), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(6.dp))

            // 播放控制区：官方组件
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                IconToggleButton(
                    checked = shuffle,
                    onCheckedChange = { onToggleShuffle() }
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "随机播放")
                }
                IconButton(onClick = onPrev) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一曲",
                        modifier = Modifier.size(40.dp)
                    )
                }
                ExpressivePlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlay
                )
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一曲",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { onToggleRepeat() }
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

            Spacer(Modifier.height(16.dp))
        }
    }
}
