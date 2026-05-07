package com.example.smsringer

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsDiagnostics(context: Context) {
    private val prefs = context.getSharedPreferences("sms_diagnostics", Context.MODE_PRIVATE)

    fun saveStatus(status: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        prefs.edit()
            .putString(KEY_LAST_STATUS, "$time $status")
            .apply()
    }

    fun loadStatus(): String {
        return prefs.getString(KEY_LAST_STATUS, "").orEmpty()
    }

    companion object {
        private const val KEY_LAST_STATUS = "last_status"
    }
}
