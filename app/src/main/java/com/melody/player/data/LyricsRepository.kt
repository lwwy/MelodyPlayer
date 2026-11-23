package com.melody.player.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.melody.player.model.LyricLine
import com.melody.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * 本地歌词加载：优先读取音频文件内嵌的 ID3 歌词（SYLT/USLT 帧），
 * 其次尝试读取同目录同名 .lrc 文件。全部在本地完成，不联网。
 *
 * 解析结果统一为带时间戳的 [LyricLine] 列表，供播放页做滚动高亮。
 */
class LyricsRepository(private val context: Context) {

    suspend fun loadLyrics(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        val raw = readEmbeddedLyrics(song)
            ?: readSidecarLrc(song)
            ?: return@withContext emptyList()
        parseLrc(raw).sortedBy { it.timeMs }
    }

    /** 从音频文件头部的 ID3v2 标签里提取歌词文本。 */
    private fun readEmbeddedLyrics(song: Song): String? = try {
        context.contentResolver.openInputStream(Uri.parse(song.uriString))?.use { input ->
            run label@ {
                val header = ByteArray(10)
                if (input.read(header) != HEADER_SIZE) return@label null
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return@label null
                }
                val major = header[3].toInt() and 0xFF
                val tagSize = synchsafe(header, 6).coerceAtMost(MAX_TAG_SIZE)
                val tag = ByteArray(tagSize)
                var read = 0
                while (read < tagSize) {
                    val n = input.read(tag, read, tagSize - read)
                    if (n <= 0) break
                    read += n
                }
                parseId3Frames(tag, read, major)
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "readEmbeddedLyrics failed: ${e.message}")
        null
    }

    /** 遍历 ID3v2 frame，优先返回 SYLT（带逐字时间），其次 USLT（整段文本）。 */
    private fun parseId3Frames(tag: ByteArray, len: Int, major: Int): String? {
        var pos = 0
        var uslt: String? = null
        while (pos + FRAME_HEADER_SIZE <= len) {
            val id = String(tag, pos, 3, Charsets.ISO_8859_1)
            if (id.firstOrNull()?.isLetter() != true) break // padding 或结束
            val frameSize = if (major == 4) {
                synchsafe(tag, pos + 4)
            } else {
                ((tag[pos + 4].toInt() and 0xFF) shl 24) or
                    ((tag[pos + 5].toInt() and 0xFF) shl 16) or
                    ((tag[pos + 6].toInt() and 0xFF) shl 8) or
                    (tag[pos + 7].toInt() and 0xFF)
            }
            if (frameSize <= 0 || pos + FRAME_HEADER_SIZE + frameSize > len) break
            val data = tag.copyOfRange(pos + FRAME_HEADER_SIZE, pos + FRAME_HEADER_SIZE + frameSize)
            when (id) {
                "SYLT" -> parseSylt(data)?.let { return it }
                "USLT" -> if (uslt == null) uslt = parseUslt(data)
            }
            pos += FRAME_HEADER_SIZE + frameSize
        }
        return uslt
    }

    /** USLT: 编码(1) + 语言(3) + 描述(字符串终止) + 歌词文本 */
    private fun parseUslt(data: ByteArray): String? {
        if (data.size < 4) return null
        val enc = data[0].toInt()
        val charset = charsetFor(enc)
        val term = terminator(enc)
        var p = 4
        p = skipString(data, p, term)
        if (p > data.size) return null
        return String(data, p, data.size - p, charset).trim().takeIf { it.isNotBlank() }
    }

    /**
     * SYLT: 编码(1) + 语言(3) + 时间格式(1) + 类型(1) + 描述(终止) +
     * 然后重复 [文本(终止) + 时间戳(4 字节大端毫秒)]。
     * 转成 [mm:ss.xx]文本 的 LRC 行，再走统一解析。
     */
    private fun parseSylt(data: ByteArray): String? {
        if (data.size < 7) return null
        val enc = data[0].toInt()
        val charset = charsetFor(enc)
        val term = terminator(enc)
        var p = skipString(data, 6, term) // 跳过描述（enc+lang+format+type 共 6 字节后）
        val sb = StringBuilder()
        while (p + term <= data.size) {
            val textStart = p
            p = skipString(data, p, term)
            if (p > data.size) break
            val text = String(data, textStart, p - textStart, charset)
            if (p + 4 > data.size) break
            val ts = ((data[p].toInt() and 0xFF) shl 24) or
                ((data[p + 1].toInt() and 0xFF) shl 16) or
                ((data[p + 2].toInt() and 0xFF) shl 8) or
                (data[p + 3].toInt() and 0xFF)
            p += 4
            if (text.isNotBlank()) {
                val min = ts / 60_000
                val sec = (ts % 60_000) / 1_000
                val cs = (ts % 1_000) / 10
                sb.append("[%02d:%02d.%02d]".format(min, sec, cs)).append(text).append('\n')
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    /** 尽力读取与音频同目录、同文件名的 .lrc 文件（部分系统/权限下可能不可用）。 */
    private fun readSidecarLrc(song: Song): String? {
        return try {
            val audioUri = Uri.parse(song.uriString)
            val proj = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH
            )
            var name: String? = null
            var relativePath: String? = null
            val found = context.contentResolver.query(audioUri, proj, null, null, null)?.use { c ->
                if (!c.moveToFirst()) false else {
                    name = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                    relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH))
                    } else null
                    true
                }
            } ?: false
            if (!found || name.isNullOrBlank()) return null
            val lrcName = name!!.substringBeforeLast('.') + ".lrc"
            val sel = mutableListOf("${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?")
            val args = mutableListOf(lrcName)
            if (!relativePath.isNullOrBlank()) {
                sel.add("${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?")
                args.add(relativePath!!)
            }
            val fProj = arrayOf(MediaStore.Files.FileColumns._ID)
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                fProj,
                sel.joinToString(" AND "),
                args.toTypedArray(),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    val lrcUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
                    context.contentResolver.openInputStream(lrcUri)?.bufferedReader()?.use { it.readText() }
                } else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "readSidecarLrc failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "LyricsRepository"
        private const val HEADER_SIZE = 10
        private const val FRAME_HEADER_SIZE = 10
        private const val MAX_TAG_SIZE = 1024 * 1024

        private val LRC_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

        private fun synchsafe(b: ByteArray, offset: Int): Int =
            ((b[offset].toInt() and 0x7F) shl 21) or
                ((b[offset + 1].toInt() and 0x7F) shl 14) or
                ((b[offset + 2].toInt() and 0x7F) shl 7) or
                (b[offset + 3].toInt() and 0x7F)

        private fun charsetFor(enc: Int): Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // 含 BOM
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }

        private fun terminator(enc: Int): Int = if (enc == 1 || enc == 2) 2 else 1

        private fun isTerminator(data: ByteArray, pos: Int, enc: Int): Boolean {
            if (enc == 1 || enc == 2) {
                return pos + 1 < data.size && data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()
            }
            return pos < data.size && data[pos] == 0.toByte()
        }

        /** 从 pos 起跳过一个以 terminator 结尾的字符串，返回 terminator 之后的位置。 */
        private fun skipString(data: ByteArray, pos: Int, term: Int): Int {
            var p = pos
            while (p + term <= data.size && !isTerminator(data, p, if (term == 2) 2 else 0)) p += term
            return (p + term).coerceAtMost(data.size)
        }

        /** 解析 LRC 文本：一行可含多个时间戳，如 [00:12.00][00:15.00]歌词。 */
        fun parseLrc(raw: String): List<LyricLine> {
            val result = mutableListOf<LyricLine>()
            raw.lines().forEach { line ->
                val matcher = LRC_PATTERN.matcher(line)
                val times = mutableListOf<Long>()
                var lastEnd = 0
                while (matcher.find()) {
                    val min = matcher.group(1)!!.toLong()
                    val sec = matcher.group(2)!!.toLong()
                    val fracRaw = matcher.group(3)
                    val ms = if (fracRaw.isNullOrEmpty()) 0L else {
                        val v = fracRaw.toLong()
                        when {
                            fracRaw.length >= 3 -> v
                            fracRaw.length == 2 -> v * 10
                            else -> v * 100
                        }
                    }
                    times.add(min * 60_000 + sec * 1_000 + ms)
                    lastEnd = matcher.end()
                }
                if (times.isEmpty()) return@forEach
                val text = line.substring(lastEnd).trim()
                times.forEach { result.add(LyricLine(it, text)) }
            }
            return result
        }
    }
}
