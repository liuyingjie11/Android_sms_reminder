package com.example.smsringer

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.provider.Telephony
import android.util.Log

class SmsObserverService : Service() {
    private var contentObserver: ContentObserver? = null
    private var lastProcessedId: Long = -1
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        ensureObserverNotificationChannel(this)
        startForeground(OBSERVER_NOTIFICATION_ID, buildNotification())
        registerObserver()
        Log.i(TAG, "SmsObserverService created, observer registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_PLAYBACK -> {
                stopPlayback()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback()
        unregisterObserver()
        Log.i(TAG, "SmsObserverService destroyed")
        super.onDestroy()
    }

    private fun registerObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                checkLatestSms()
            }
        }
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            contentObserver!!
        )
    }

    private fun unregisterObserver() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        contentObserver = null
    }

    private fun checkLatestSms() {
        val cursor = try {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("_id", "address", "body", "date"),
                null, null,
                "date DESC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query SMS inbox: ${e.message}")
            return
        } ?: return

        cursor.use {
            if (!it.moveToFirst()) return
            val id = it.getLong(0)
            if (id == lastProcessedId) return
            lastProcessedId = id

            val sender = it.getString(1).orEmpty()
            val body = it.getString(2).orEmpty()
            val rule = RuleStore(this).load()
            val diagnostics = SmsDiagnostics(this)

            val matched = rule.matches(sender, body)
            val detail = "来源=ContentObserver 号码关键词=[${rule.phoneKeyword}] 内容关键词=[${rule.contentKeyword}] 发件人=[$sender] 短信=[${body.take(30)}]"
            Log.i(TAG, "Observer matching result=$matched, $detail")

            if (matched) {
                startPlayback(rule.ringtoneUri, rule.volume, rule.vibrateWhenPlaying)
                diagnostics.saveStatus("已匹配，已启动铃声；$detail")
            } else {
                diagnostics.saveStatus("未匹配（ContentObserver）；$detail")
            }
        }
    }

    private fun startPlayback(uri: Uri, volume: Float, vibrateEnabled: Boolean) {
        stopPlayback()
        isPlaying = true

        val player = createPlayer(uri, volume)
        if (player != null) {
            mediaPlayer = player
            requestAudioFocus()
            mediaPlayer?.start()
            startVibration(vibrateEnabled)
            updatePlaybackNotification()
            return
        }

        Log.w(TAG, "Primary ringtone failed, falling back to default; uri=$uri")
        val fallback = createPlayer(Settings.System.DEFAULT_NOTIFICATION_URI, volume)
        if (fallback != null) {
            mediaPlayer = fallback
            requestAudioFocus()
            mediaPlayer?.start()
            startVibration(vibrateEnabled)
            updatePlaybackNotification()
            return
        }

        Log.e(TAG, "Both ringtones failed to play")
        SmsDiagnostics(this).saveStatus("铃声播放失败：铃声文件无法加载")
        isPlaying = false
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaPlayer: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private fun requestAudioFocus() {
        if (audioManager == null) {
            audioManager = getSystemService(AudioManager::class.java)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        audioFocusRequest = null
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

    private fun stopPlayback() {
        stopVibration()
        abandonAudioFocus()
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        updatePlaybackNotification()
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun updatePlaybackNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(OBSERVER_NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            RingtoneService.pendingIntentFlags()
        )

        if (isPlaying) {
            val stopPlaybackIntent = PendingIntent.getService(
                this, 2,
                Intent(this, SmsObserverService::class.java).setAction(ACTION_STOP_PLAYBACK),
                RingtoneService.pendingIntentFlags()
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }
            return builder
                .setSmallIcon(R.drawable.ic_app_icon)
                .setContentTitle("短信铃声提醒")
                .setContentText("已匹配短信规则，正在播放铃声")
                .setContentIntent(openIntent)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .addAction(R.drawable.ic_music, "停止播放", stopPlaybackIntent)
                .build()
        }

        val stopObserverIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SmsObserverService::class.java).setAction(ACTION_STOP),
            RingtoneService.pendingIntentFlags()
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle("短信监听中")
            .setContentText("正在后台监听短信，点击打开设置")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .addAction(R.drawable.ic_music, "停止监听", stopObserverIntent)
            .build()
    }

    companion object {
        private const val TAG = "SmsObserverService"
        private const val CHANNEL_ID = "sms_observer"
        private const val OBSERVER_NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "com.example.smsringer.action.STOP_OBSERVER"
        private const val ACTION_STOP_PLAYBACK = "com.example.smsringer.action.STOP_OBSERVER_PLAYBACK"

        fun start(context: Context) {
            val intent = Intent(context, SmsObserverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SmsObserverService::class.java).setAction(ACTION_STOP)
            )
        }

        fun stopPlayback(context: Context) {
            context.startService(
                Intent(context, SmsObserverService::class.java).setAction(ACTION_STOP_PLAYBACK)
            )
        }

        fun isRunning(context: Context): Boolean {
            return try {
                val manager = context.getSystemService(ActivityManager::class.java)
                manager.getRunningServices(Integer.MAX_VALUE)
                    ?.any { it.service.className == SmsObserverService::class.java.name }
                    ?: false
            } catch (_: Exception) {
                false
            }
        }

        fun ensureObserverNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信后台监听",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "保持短信监听在后台运行，匹配时切换为铃声提醒"
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
