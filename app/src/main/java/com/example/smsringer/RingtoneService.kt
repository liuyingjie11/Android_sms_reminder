package com.example.smsringer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

class RingtoneService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        val rule = RuleStore(this).load()
        startForeground(NOTIFICATION_ID, buildNotification())
        startPlayback(rule.ringtoneUri, rule.volume)
        startVibration(rule.vibrateWhenPlaying)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun startPlayback(uri: Uri, volume: Float) {
        stopPlayback()
        mediaPlayer = createPlayer(uri, volume) ?: createPlayer(Settings.System.DEFAULT_NOTIFICATION_URI, volume)
        mediaPlayer?.start()
    }

    private fun createPlayer(uri: Uri, volume: Float): MediaPlayer? {
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopPlayback() {
        stopVibration()
        mediaPlayer?.run {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun startVibration(enabled: Boolean) {
        if (!enabled) return
        vibrator = getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0L, 700L, 700L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags()
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RingtoneService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags()
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_app_icon)
            .setTicker("短信铃声提醒")
            .setContentTitle("短信铃声提醒")
            .setContentText("已匹配短信规则，正在播放铃声")
            .setStyle(Notification.BigTextStyle().bigText("已匹配短信规则，正在播放铃声。点击停止播放可立即关闭提醒。"))
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .addAction(R.drawable.ic_music, "停止播放", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        ensurePlaybackNotificationChannel(this)
    }

    companion object {
        const val CHANNEL_ID = "sms_ringer_playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.smsringer.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RingtoneService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RingtoneService::class.java).setAction(ACTION_STOP))
        }

        fun ensurePlaybackNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信铃声提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "短信匹配后播放铃声时显示"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun pendingIntentFlags(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
    }
}
