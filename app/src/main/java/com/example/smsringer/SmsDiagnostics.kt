package com.example.smsringer

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsDiagnostics(context: Context) {
    private val prefs = context.getSharedPreferences("sms_diagnostics", Context.MODE_PRIVATE)

    fun saveStatus(status: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        val entry = "$time $status"
        val recentStatuses = loadRecentStatuses()
            .toMutableList()
            .apply { add(0, entry) }
            .take(MAX_STATUS_COUNT)
        val recentStatusesJson = JSONArray().apply {
            recentStatuses.forEach { put(it) }
        }.toString()

        prefs.edit()
            .putString(KEY_LAST_STATUS, entry)
            .putString(KEY_RECENT_STATUSES, recentStatusesJson)
            .apply()
    }

    fun loadStatus(): String {
        return loadRecentStatuses().firstOrNull()
            ?: prefs.getString(KEY_LAST_STATUS, "").orEmpty()
    }

    fun loadRecentStatuses(): List<String> {
        val rawStatuses = prefs.getString(KEY_RECENT_STATUSES, null)
        if (!rawStatuses.isNullOrBlank()) {
            try {
                val jsonStatuses = JSONArray(rawStatuses)
                val statuses = mutableListOf<String>()
                for (index in 0 until minOf(jsonStatuses.length(), MAX_STATUS_COUNT)) {
                    val status = jsonStatuses.optString(index)
                    if (status.isNotBlank()) {
                        statuses += status
                    }
                }
                if (statuses.isNotEmpty()) {
                    return statuses
                }
            } catch (_: Exception) {
                // Fall back to the legacy single-status value below.
            }
        }

        return prefs.getString(KEY_LAST_STATUS, "").orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let { listOf(it) }
            .orEmpty()
    }

    companion object {
        private const val MAX_STATUS_COUNT = 10
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_RECENT_STATUSES = "recent_statuses"
    }
}
