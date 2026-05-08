package com.example.smsringer

import android.content.Context
import android.net.Uri
import android.provider.Settings

data class AlertRule(
    val phoneKeyword: String,
    val contentKeyword: String,
    val ringtoneUri: Uri,
    val volume: Float,
    val vibrateWhenPlaying: Boolean,
    val hideFromRecents: Boolean,
    val smsObserverEnabled: Boolean
) {
    fun matches(sender: String, message: String): Boolean {
        val phoneMatches = splitKeywords(phoneKeyword).any { sender.contains(it, ignoreCase = true) }
        val contentMatches = splitKeywords(contentKeyword).any { message.contains(it, ignoreCase = true) }
        return phoneMatches || contentMatches
    }

    private fun splitKeywords(rawValue: String): List<String> {
        return rawValue
            .split(',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
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
            volume = prefs.getFloat(KEY_VOLUME, 0.8f).coerceIn(0f, 1f),
            vibrateWhenPlaying = prefs.getBoolean(KEY_VIBRATE, false),
            hideFromRecents = prefs.getBoolean(KEY_HIDE_FROM_RECENTS, false),
            smsObserverEnabled = prefs.getBoolean(KEY_SMS_OBSERVER_ENABLED, false)
        )
    }

    fun save(
        phoneKeyword: String,
        contentKeyword: String,
        ringtoneUri: Uri,
        volume: Float,
        vibrateWhenPlaying: Boolean,
        hideFromRecents: Boolean,
        smsObserverEnabled: Boolean
    ) {
        prefs.edit()
            .putString(KEY_PHONE, phoneKeyword.trim())
            .putString(KEY_CONTENT, contentKeyword.trim())
            .putString(KEY_RINGTONE_URI, ringtoneUri.toString())
            .putFloat(KEY_VOLUME, volume.coerceIn(0f, 1f))
            .putBoolean(KEY_VIBRATE, vibrateWhenPlaying)
            .putBoolean(KEY_HIDE_FROM_RECENTS, hideFromRecents)
            .putBoolean(KEY_SMS_OBSERVER_ENABLED, smsObserverEnabled)
            .apply()
    }

    companion object {
        private const val KEY_PHONE = "phone"
        private const val KEY_CONTENT = "content"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_VOLUME = "volume"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
        private const val KEY_SMS_OBSERVER_ENABLED = "sms_observer_enabled"
    }
}
