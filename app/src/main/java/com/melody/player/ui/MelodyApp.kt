package com.melody.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MotionScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.melody.player.ui.screen.PermissionScreen
import com.melody.player.ui.screen.PlayerScreen
import com.melody.player.ui.screen.SongsScreen
import kotlinx.coroutines.delay

@Composable
fun MelodyApp(
    permissionGranted: Boolean,
    permissionPermanentlyDenied: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    // Material 3 Expressive 动效 token：跟随主题的运动方案，spatial 用于位移（带弹性回弹）
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.loadLibrary()
    }

    // 播放进度轮询
    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            viewModel.updateProgress()
            delay(500L)
        }
    }

    // 回到前台时重扫曲库（补上在其它 App 里添加/删除歌曲的情况）
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permissionGranted) viewModel.refreshLibrarySilently()
    }

    if (!permissionGranted) {
        PermissionScreen(
            permanentlyDenied = permissionPermanentlyDenied,
            onRequest = onRequestPermission,
            onOpenSettings = onOpenSettings
        )
        return
    }

    // 返回键：收起播放页
    BackHandler(enabled = showPlayer) { showPlayer = false }

    Box(modifier = Modifier.fillMaxSize()) {
        // 列表铺满整个屏幕。底部留 92dp 内容 padding，避免最后一项被悬浮的迷你播放器遮挡。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            SongsScreen(
                modifier = Modifier.fillMaxSize(),
                songs = uiState.songs,
                isLoading = uiState.isLoading,
                currentSongId = uiState.currentSong?.id,
                isPlaying = uiState.isPlaying,
                bottomContentPadding = 92.dp,
                onSongClick = { viewModel.playSong(it) }
            )
        }

        // 悬浮迷你播放器：覆盖在列表之上（BottomCenter），实色主题卡片。
        // navigationBarsPadding 避开手势导航条，再留 18dp 让卡片圆角与阴影不被裁切。
        AnimatedVisibility(
            visible = uiState.currentSong != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>()
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = motionScheme.fastSpatialSpec<IntOffset>()
            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        ) {
            uiState.currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    progress = uiState.progressMs.toFloat() / uiState.durationMs.coerceAtLeast(1L),
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onExpand = { showPlayer = true }
                )
            }
        }

        // 全屏播放页：从底部弹出 / 收回，使用 M3 Expressive spatial spring 动效（弹性回弹）
        AnimatedVisibility(
            visible = showPlayer && uiState.currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>()
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = motionScheme.fastSpatialSpec<IntOffset>()
            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        ) {
            uiState.currentSong?.let { song ->
                PlayerScreen(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    progressMs = uiState.progressMs,
                    durationMs = uiState.durationMs,
                    repeatMode = uiState.repeatMode,
                    shuffle = uiState.shuffle,
                    lyrics = uiState.lyrics,
                    lyricsSongId = uiState.lyricsSongId,
                    navDirection = uiState.navDirection,
                    coverColor = uiState.coverColor,
                    onDismiss = { showPlayer = false },
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipNext() },
                    onPrev = { viewModel.skipPrevious() },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onToggleShuffle = { viewModel.toggleShuffle() }
                )
            }
        }
    }
}
