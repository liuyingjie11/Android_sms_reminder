package com.example.smsringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val diagnostics = SmsDiagnostics(context)
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            diagnostics.saveStatus("收到短信广播但内容为空；action=$action")
            Log.i(TAG, "Received SMS broadcast with empty messages; action=$action")
            return
        }

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val rule = RuleStore(context).load()

        val matched = rule.matches(sender, body)
        val detail = "action=${action?.takeLast(15)} 号码关键词=[${rule.phoneKeyword}] 内容关键词=[${rule.contentKeyword}] 发件人=[$sender] 短信=[${body.take(30)}]"
        Log.i(TAG, "Matching result=$matched, $detail")

        if (matched) {
            try {
                RingtoneService.start(context)
                diagnostics.saveStatus("已匹配，已启动铃声；$detail")
                Log.i(TAG, "SMS matched and ringtone service started")
            } catch (error: Exception) {
                diagnostics.saveStatus("已匹配，但启动铃声失败：${error.javaClass.simpleName}；$detail")
                Log.e("SmsReceiver", "Unable to start ringtone service", error)
            }
        } else {
            diagnostics.saveStatus("未匹配；$detail")
            Log.i(TAG, "SMS received but not matched")
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
