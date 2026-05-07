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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log

class SmsObserverService : Service() {
    private var contentObserver: ContentObserver? = null
    private var lastProcessedId: Long = -1

    override fun onCreate() {
        super.onCreate()
        ensureObserverNotificationChannel(this)
        startForeground(OBSERVER_NOTIFICATION_ID, buildNotification())
        registerObserver()
        Log.i(TAG, "SmsObserverService created, observer registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
                try {
                    RingtoneService.start(this)
                    diagnostics.saveStatus("已匹配，已启动铃声；$detail")
                } catch (e: Exception) {
                    diagnostics.saveStatus("已匹配，但启动铃声失败：${e.javaClass.simpleName}；$detail")
                }
            } else {
                diagnostics.saveStatus("未匹配（ContentObserver）；$detail")
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            RingtoneService.pendingIntentFlags()
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
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
            .addAction(R.drawable.ic_music, "停止监听", stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "SmsObserverService"
        private const val CHANNEL_ID = "sms_observer"
        private const val OBSERVER_NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "com.example.smsringer.action.STOP_OBSERVER"

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

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java)
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == SmsObserverService::class.java.name }
        }

        fun ensureObserverNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信后台监听",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持短信监听在后台运行"
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
