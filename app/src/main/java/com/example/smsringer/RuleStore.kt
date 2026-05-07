package com.example.smsringer

import android.content.Context
import android.net.Uri
import android.provider.Settings

data class AlertRule(
    val phoneKeyword: String,
    val contentKeyword: String,
    val ringtoneUri: Uri,
    val volume: Float
) {
    fun matches(sender: String, message: String): Boolean {
        val phoneMatches = phoneKeyword.isNotBlank() && sender.contains(phoneKeyword, ignoreCase = true)
        val contentMatches = contentKeyword.isNotBlank() && message.contains(contentKeyword, ignoreCase = true)
        return phoneMatches || contentMatches
    }
}

class RuleStore(context: Context) {
    private val prefs = context.getSharedPreferences("alert_rule", Context.MODE_PRIVATE)

    fun load(): AlertRule {
        val uriText = prefs.getString(KEY_RINGTONE_URI, null)
        val ringtoneUri = uriText?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI
        return AlertRule(
            phoneKeyword = prefs.getString(KEY_PHONE, "").orEmpty(),
            contentKeyword = prefs.getString(KEY_CONTENT, "").orEmpty(),
            ringtoneUri = ringtoneUri,
            volume = prefs.getFloat(KEY_VOLUME, 0.8f).coerceIn(0f, 1f)
        )
    }

    fun save(phoneKeyword: String, contentKeyword: String, ringtoneUri: Uri, volume: Float) {
        prefs.edit()
            .putString(KEY_PHONE, phoneKeyword.trim())
            .putString(KEY_CONTENT, contentKeyword.trim())
            .putString(KEY_RINGTONE_URI, ringtoneUri.toString())
            .putFloat(KEY_VOLUME, volume.coerceIn(0f, 1f))
            .apply()
    }

    companion object {
        private const val KEY_PHONE = "phone"
        private const val KEY_CONTENT = "content"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_VOLUME = "volume"
    }
}
