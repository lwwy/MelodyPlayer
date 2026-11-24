package com.melody.player.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.MoreExecutors
import com.melody.player.data.LyricsRepository
import com.melody.player.data.MusicRepository
import com.melody.player.data.PlayerPrefs
import com.melody.player.model.LyricLine
import com.melody.player.model.Song
import com.melody.player.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    // 歌词所属的歌曲 id，供播放页按 id 做整块溶解（新旧两层歌词内容才真正不同）
    val lyricsSongId: Long = -1L,
    // 切歌方向：1 = 下一曲，-1 = 上一曲；供播放页决定封面/信息的滑入方向
    val navDirection: Int = 1,
    // 当前封面提取的主色（ARGB），驱动播放页动态背景；null 时回退主题色
    val coverColor: Int? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application.applicationContext)
    private val lyricsRepository = LyricsRepository(application.applicationContext)
    private val prefs = PlayerPrefs(application.applicationContext)

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            repeatMode = prefs.savedRepeatMode,
            shuffle = prefs.savedShuffle
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var allSongs: List<Song> = emptyList()

    /** 从歌曲列表构造 MediaItem，播放队列与冷启动恢复共用。 */
    private fun buildMediaItems(): List<MediaItem> = allSongs.map { s ->
        MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uriString)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(s.albumArtUri?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
    }

    // 媒体库变化监听：外部添加/删除歌曲后延迟防抖重扫（静默，不闪 loading）
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshLibrarySilently() }
    private val mediaObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            scheduleRefresh()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scheduleRefresh()
        }
    }

    private val controllerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.value = _uiState.value.copy(
                isPlaying = playbackState == Player.STATE_READY && (controller?.isPlaying == true)
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                allSongs.find { it.id == id }?.let { song ->
                    val pos = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    _uiState.value = _uiState.value.copy(
                        currentSong = song,
                        durationMs = song.durationMs,
                        progressMs = pos,
                        coverColor = null
                    )
                    prefs.savePosition(song.id, pos)
                    loadLyricsFor(song)
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.value = _uiState.value.copy(shuffle = shuffleModeEnabled)
        }
    }

    init {
        connectController()
        registerMediaObserver()
    }

    private fun registerMediaObserver() {
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
        } catch (e: Exception) {
            // 权限未授予或系统限制时忽略，回前台仍会主动重扫
        }
    }

    /** 媒体库变化防抖：500ms 内多次变化合并为一次刷新。 */
    private fun scheduleRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, 500L)
    }

    /**
     * 静默重扫曲库：只更新歌曲列表，不切换 isLoading（避免列表闪一下加载态）。
     * 供媒体库变化监听与回前台重扫调用；当前播放状态不受影响。
     */
    fun refreshLibrarySilently() {
        viewModelScope.launch {
            val songs = repository.querySongs()
            allSongs = songs
            _uiState.value = _uiState.value.copy(songs = songs)
        }
    }

    private fun connectController() {
        val ctx = getApplication<Application>()
        val sessionToken = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, sessionToken).buildAsync()
        future.addListener({
            try {
                controller = future.get().apply {
                    addListener(controllerListener)
                }
                // 曲库加载完成后再恢复播放状态（依赖 allSongs 匹配媒体项）
                loadLibrary(onReady = { restorePlaybackState() })
            } catch (e: Exception) {
                // 服务尚未就绪时，仍先从 MediaStore 读取曲库用于展示
                loadLibrary(onReady = { restorePlaybackState() })
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * 从当前 MediaController 恢复播放状态。
     * 关键：Activity 重建 / 从后台返回 / 进程重启后，前台服务仍在播放，
     * 但 ViewModel 是全新的，必须主动读取会话里的当前媒体项与播放状态，
     * 否则 currentSong 为空导致迷你播放器与播放页不显示。
     */
    private fun restorePlaybackState() {
        val c = controller ?: return

        // 场景一：播放会话仍在（进程未死，如切后台再回来）——同步会话里的模式与进度
        val mediaItem = c.currentMediaItem
        if (mediaItem != null) {
            val id = mediaItem.mediaId.toLongOrNull()
            val song = id?.let { sid -> allSongs.find { it.id == sid } }
            if (song != null) {
                // 以持久化的模式为准（清后台重建会话时会话模式会丢）
                c.repeatMode = prefs.savedRepeatMode
                c.shuffleModeEnabled = prefs.savedShuffle
                _uiState.value = _uiState.value.copy(
                    currentSong = song,
                    durationMs = if (c.duration > 0) c.duration else song.durationMs,
                    progressMs = c.currentPosition.coerceAtLeast(0L),
                    isPlaying = c.isPlaying,
                    repeatMode = prefs.savedRepeatMode,
                    shuffle = prefs.savedShuffle,
                    coverColor = null
                )
                loadLyricsFor(song)
            }
            return
        }

        // 场景二：进程被清理后冷启动，会话是空的——从持久化状态重建队列并定位到上次位置
        c.repeatMode = prefs.savedRepeatMode
        c.shuffleModeEnabled = prefs.savedShuffle
        if (!prefs.hasSavedSong || allSongs.isEmpty()) return
        val savedId = prefs.savedSongId
        val index = allSongs.indexOfFirst { it.id == savedId }
        if (index < 0) return
        val items = buildMediaItems()
        val savedPos = prefs.savedPositionMs
        c.setMediaItems(items, index, savedPos.coerceAtLeast(0L))
        c.prepare()
        // 冷启动保持暂停，只恢复位置，不自动播放
        val song = allSongs[index]
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            durationMs = song.durationMs,
            progressMs = savedPos,
            isPlaying = false,
            repeatMode = prefs.savedRepeatMode,
            shuffle = prefs.savedShuffle,
            coverColor = null
        )
        loadLyricsFor(song)
    }

    /**
     * 切歌时同步加载歌词与提取封面主色。
     * 不再先清空歌词——歌词区改由 PlayerScreen 用 AnimatedContent 按 song.id 整块溶解，
     * 若这里先清空会导致过渡期歌词区空白、动画丢失。
     */
    private fun loadLyricsFor(song: Song) {
        viewModelScope.launch {
            val lines = lyricsRepository.loadLyrics(song)
            // 仅在仍是当前歌曲时写入，防止快速切歌导致旧歌词覆盖
            if (_uiState.value.currentSong?.id == song.id) {
                _uiState.value = _uiState.value.copy(lyrics = lines, lyricsSongId = song.id)
            }
        }
        loadCoverColor(song)
    }

    /** 从专辑封面提取主色（vibrant 优先），供播放页动态背景平滑过渡。 */
    private fun loadCoverColor(song: Song) {
        viewModelScope.launch {
            val argb = withContext(Dispatchers.IO) {
                val uri = song.albumArtUri ?: return@withContext null
                try {
                    val ctx = getApplication<Application>()
                    val request = ImageRequest.Builder(ctx).data(uri).size(96, 96).build()
                    val result = Coil.imageLoader(ctx).execute(request)
                    val bitmap = (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                        val swatch = palette.vibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.darkVibrantSwatch
                        swatch?.rgb
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            if (argb != null && _uiState.value.currentSong?.id == song.id) {
                _uiState.value = _uiState.value.copy(coverColor = argb)
            }
        }
    }

    fun loadLibrary(onReady: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val songs = repository.querySongs()
            allSongs = songs
            _uiState.value = _uiState.value.copy(
                songs = songs,
                isLoading = false
            )
            onReady?.invoke()
        }
    }

    fun playSong(song: Song) {
        val c = controller ?: return
        val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        c.setMediaItems(buildMediaItems(), startIndex, 0L)
        c.prepare()
        c.play()
        prefs.savePosition(song.id, 0L)
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            durationMs = song.durationMs,
            progressMs = 0L,
            isPlaying = true,
            coverColor = null
        )
        loadLyricsFor(song)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() {
        _uiState.value = _uiState.value.copy(navDirection = 1)
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        _uiState.value = _uiState.value.copy(navDirection = -1)
        controller?.seekToPreviousMediaItem()
    }

    /**
     * 跳转到指定位置。立即同步 UI 进度（修复暂停时不轮询导致的回弹），
     * 并在暂停状态下自动续播——用户拖动进度条后即从新位置继续播放。
     */
    fun seekTo(ms: Long) {
        val c = controller ?: return
        val target = ms.coerceAtLeast(0L)
        c.seekTo(target)
        val wasPlaying = c.isPlaying
        if (!wasPlaying) c.play()
        _uiState.value = _uiState.value.copy(
            progressMs = target,
            isPlaying = true
        )
        prefs.savePosition(_uiState.value.currentSong?.id ?: -1L, target)
    }

    fun updateProgress() {
        controller?.let { c ->
            val pos = c.currentPosition
            _uiState.value = _uiState.value.copy(
                progressMs = pos,
                durationMs = c.duration.coerceAtLeast(0L),
                isPlaying = c.isPlaying
            )
            // 播放中记录位置供冷启动续播：用节流写入，避免每 500ms 一次磁盘 I/O
            _uiState.value.currentSong?.id?.let { prefs.savePositionThrottled(it, pos) }
        }
    }

    fun toggleRepeat() {
        val c = controller ?: return
        // 官方 Media3 RepeatButtonState 顺序：关 → 单曲 → 列表 → 关
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        prefs.saveRepeatMode(next)
        // 即时更新 UI，确保点击立刻有反馈（不依赖 session 异步回调）
        _uiState.value = _uiState.value.copy(repeatMode = next)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val next = !c.shuffleModeEnabled
        c.shuffleModeEnabled = next
        prefs.saveShuffle(next)
        _uiState.value = _uiState.value.copy(shuffle = next)
    }

    override fun onCleared() {
        // ViewModel 销毁前强制落盘一次当前进度，弥补节流窗口内未写入的位置
        val currentId = _uiState.value.currentSong?.id
        if (currentId != null && currentId >= 0) {
            prefs.savePosition(currentId, _uiState.value.progressMs)
        }
        mainHandler.removeCallbacks(refreshRunnable)
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
        } catch (e: Exception) {
            // 未注册成功时忽略
        }
        controller?.removeListener(controllerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
