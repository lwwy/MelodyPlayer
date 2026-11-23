package com.melody.player.data

import android.content.Context

/**
 * 轻量播放状态持久化：把循环模式、随机、当前歌曲与播放位置写入 SharedPreferences。
 * 目的：进程被系统清理（清后台）后冷启动仍能恢复用户上次的选择与收听位置。
 */
class PlayerPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val savedSongId: Long get() = sp.getLong(KEY_SONG_ID, -1L)
    val savedPositionMs: Long get() = sp.getLong(KEY_POSITION, 0L)
    val savedRepeatMode: Int get() = sp.getInt(KEY_REPEAT, 0)
    val savedShuffle: Boolean get() = sp.getBoolean(KEY_SHUFFLE, false)
    val hasSavedSong: Boolean get() = savedSongId >= 0

    /** 保存播放位置（切歌、拖动、轮询时调用）。 */
    fun savePosition(songId: Long, positionMs: Long) {
        sp.edit()
            .putLong(KEY_SONG_ID, songId)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
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
    }
}
