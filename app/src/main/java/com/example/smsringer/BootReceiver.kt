package com.example.smsringer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val rule = RuleStore(context).load()
        if (!rule.smsObserverEnabled) {
            Log.i(TAG, "Boot completed, SMS observer is disabled")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            SmsDiagnostics(context).saveStatus("开机后未启动后台监听：缺少短信读取权限")
            Log.i(TAG, "Boot completed, READ_SMS permission missing")
            return
        }

        Log.i(TAG, "Boot completed, starting SMS observer")
        SmsObserverService.start(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
