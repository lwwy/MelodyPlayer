package com.melody.player.model

/**
 * 本地音频文件对应的歌曲模型。
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val albumArtUri: String? = null
) {
    val durationText: String
        get() {
            val totalSec = durationMs / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return "%d:%02d".format(m, s)
        }
}

data class Album(
    val name: String,
    val artist: String,
    val albumArtUri: String?,
    val songCount: Int
)

/**
 * 一行带时间戳的歌词，timeMs 为该行开始显示的播放进度（毫秒）。
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)
