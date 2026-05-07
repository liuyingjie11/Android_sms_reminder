package com.example.smsringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val rule = RuleStore(context).load()
        if (rule.phoneKeyword.isNotBlank() || rule.contentKeyword.isNotBlank()) {
            Log.i(TAG, "Boot completed, starting SMS observer")
            SmsObserverService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
