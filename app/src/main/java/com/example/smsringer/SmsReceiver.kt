package com.example.smsringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val diagnostics = SmsDiagnostics(context)
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            diagnostics.saveStatus("收到真实短信广播，但短信内容为空")
            Log.i(TAG, "Received SMS broadcast with empty messages")
            return
        }

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val rule = RuleStore(context).load()

        if (rule.matches(sender, body)) {
            try {
                RingtoneService.start(context)
                diagnostics.saveStatus("真实短信已匹配，已启动铃声；号码：${sender.ifBlank { "未知" }}")
                Log.i(TAG, "SMS matched and ringtone service started. sender=$sender")
            } catch (error: Exception) {
                diagnostics.saveStatus("真实短信已匹配，但启动铃声失败：${error.javaClass.simpleName}")
                Log.e("SmsReceiver", "Unable to start ringtone service", error)
            }
        } else {
            diagnostics.saveStatus("收到真实短信但未匹配；号码：${sender.ifBlank { "未知" }}；内容：${body.take(20)}")
            Log.i(TAG, "SMS received but not matched. sender=$sender body=${body.take(40)}")
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
