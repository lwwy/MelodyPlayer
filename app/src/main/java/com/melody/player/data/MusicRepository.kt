package com.melody.player.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.melody.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 MediaStore 扫描设备本地音频，纯本地，不联网。
 */
class MusicRepository(private val context: Context) {

    suspend fun querySongs(): List<Song> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        // 只取真正的音乐文件：IS_MUSIC=1 且时长至少 30 秒，排除铃声/提示音/录音片段等
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            // 封面探测缓存：同一专辑只做一次 openFileDescriptor 存在性校验，
            // 避免 N+1 重复 I/O（同一专辑可能有多首歌）。null 也缓存，杜绝反复失败探测。
            val artUriCache = mutableMapOf<Long, String?>()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.trim().orEmpty()
                val artistRaw = cursor.getString(artistCol)?.trim().orEmpty()
                val albumRaw = cursor.getString(albumCol)?.trim().orEmpty()
                val displayName = cursor.getString(nameCol)?.trim().orEmpty()
                val duration = cursor.getLong(durCol)

                // 标题无效、且文件名也不像正常歌曲名：跳过（多为录音/语音片段）
                val titleInvalid = title.isEmpty() || title.equals("<unknown>", true)
                if (titleInvalid && displayName.isEmpty()) continue
                // 排除仍是 unknown 的占位标题，用文件名兜底展示
                val finalTitle = if (titleInvalid) displayName.substringBeforeLast('.') else title
                if (finalTitle.isBlank()) continue
                // 排除明显的录音/语音命名（如 "录音_20240101"、"Voice 001"、"record..."）
                if (isLikelyRecording(finalTitle, displayName)) continue

                val albumId = cursor.getLong(albumIdCol)
                val artUri = artUriCache.getOrPut(albumId) { getAlbumArtUri(albumId) }
                val mediaUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                ).toString()
                result.add(
                    Song(
                        id = id,
                        title = finalTitle,
                        artist = artistRaw.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "未知歌手",
                        album = albumRaw.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) } ?: "未知专辑",
                        durationMs = duration,
                        uriString = mediaUri,
                        albumArtUri = artUri
                    )
                )
            }
        }
        result
    }

    /** 按标题/文件名识别常见录音、语音备忘等非歌曲内容。 */
    private fun isLikelyRecording(title: String, displayName: String): Boolean {
        val t = (title + " " + displayName).lowercase()
        val keywords = listOf(
            "录音", "recordings", "voice ", "voice_", "voicemail", "memo",
            "whatsapp", "audio note", "语音", "通话录音", "screenshot"
        )
        return keywords.any { t.contains(it) }
    }

    /**
     * 用标准 MediaStore API 构造专辑封面 URI，并检查其是否真实存在。
     * 无封面时返回 null，交给 UI 的占位图处理。
     */
    private fun getAlbumArtUri(albumId: Long): String? {
        if (albumId <= 0) return null
        return try {
            val artUri = Uri.withAppendedPath(
                Uri.parse("content://media/external/audio/albumart"),
                albumId.toString()
            )
            context.contentResolver.openFileDescriptor(artUri, "r")?.use {
                artUri.toString()
            }
        } catch (e: Exception) {
            Log.d("MusicRepository", "no album art for albumId=$albumId: ${e.message}")
            null
        }
    }
}
