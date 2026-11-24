package com.melody.player.data

import android.content.Context
import android.os.SystemClock

/**
 * 轻量播放状态持久化：把循环模式、随机、当前歌曲与播放位置写入 SharedPreferences。
 * 目的：进程被系统清理（清后台）后冷启动仍能恢复用户上次的选择与收听位置。
 */
class PlayerPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 节流写入用的内存时间戳（单调时钟，不受系统时间调整影响）
    private var lastThrottledWriteMs = 0L
    private var lastThrottledSongId = -1L

    val savedSongId: Long get() = sp.getLong(KEY_SONG_ID, -1L)
    val savedPositionMs: Long get() = sp.getLong(KEY_POSITION, 0L)
    val savedRepeatMode: Int get() = sp.getInt(KEY_REPEAT, 0)
    val savedShuffle: Boolean get() = sp.getBoolean(KEY_SHUFFLE, false)
    val hasSavedSong: Boolean get() = savedSongId >= 0

    /** 保存播放位置（切歌、拖动、轮询时调用）。songId 非法时不写脏值。 */
    fun savePosition(songId: Long, positionMs: Long) {
        if (songId < 0) return
        sp.edit()
            .putLong(KEY_SONG_ID, songId)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    /**
     * 节流版：供高频进度轮询调用。同一首歌在 [minIntervalMs] 内只真正落盘一次，
     * 切歌（songId 变化）时立即写入，避免每 500ms 一次的高频磁盘 I/O。
     */
    fun savePositionThrottled(
        songId: Long,
        positionMs: Long,
        minIntervalMs: Long = DEFAULT_THROTTLE_MS
    ) {
        if (songId < 0) return
        val now = SystemClock.elapsedRealtime()
        if (songId == lastThrottledSongId && now - lastThrottledWriteMs < minIntervalMs) return
        savePosition(songId, positionMs)
        lastThrottledSongId = songId
        lastThrottledWriteMs = now
    }

    fun saveRepeatMode(mode: Int) {
        sp.edit().putInt(KEY_REPEAT, mode).apply()
    }

    fun saveShuffle(enabled: Boolean) {
        sp.edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "melody_player_state"
        private const val KEY_SONG_ID = "song_id"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_SHUFFLE = "shuffle"
        // 轮询写盘最小间隔 10 秒：一首 3 分钟歌约写 18 次，而非 360 次
        private const val DEFAULT_THROTTLE_MS = 10_000L
    }
}
