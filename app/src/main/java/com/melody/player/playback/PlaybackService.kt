package com.melody.player.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.melody.player.R

/**
 * 后台播放服务：承载 ExoPlayer 与 MediaSession，使锁屏/通知栏可控制播放。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 音频属性：媒体用途 + 音乐内容类型，handleAudioFocus=true 让系统管理音频焦点，
        // 来电/其他 App 播放时自动暂停或压低音量，避免混音。
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // 拔耳机/断开蓝牙时自动暂停，避免外放尴尬
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        // 自定义媒体通知：显式单色小图标，避免部分机型显示为方块。
        // Media3 1.5.0 的 setSmallIcon 是 Provider 实例方法（非 Builder 方法）。
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_play)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
