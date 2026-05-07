package com.example.smsringer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: RuleStore
    private lateinit var phoneInput: EditText
    private lateinit var contentInput: EditText
    private lateinit var mockPhoneInput: EditText
    private lateinit var mockContentInput: EditText
    private lateinit var volumeSeek: SeekBar
    private lateinit var volumeValue: TextView
    private lateinit var ringtoneName: TextView
    private lateinit var statusText: TextView
    private var selectedRingtoneUri: Uri = Settings.System.DEFAULT_NOTIFICATION_URI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = RuleStore(this)
        bindViews()
        loadRule()
        requestNeededPermissions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RINGTONE || resultCode != RESULT_OK) return

        val pickedUri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (pickedUri != null) {
            selectedRingtoneUri = pickedUri
            updateRingtoneName()
            saveRule(showToast = false)
            setStatus("已选择铃声")
        } else {
            setStatus("未选择铃声，将使用系统默认铃声")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            setStatus("需要短信和通知权限才能正常提醒")
        }
    }

    private fun bindViews() {
        phoneInput = findViewById(R.id.phoneInput)
        contentInput = findViewById(R.id.contentInput)
        mockPhoneInput = findViewById(R.id.mockPhoneInput)
        mockContentInput = findViewById(R.id.mockContentInput)
        volumeSeek = findViewById(R.id.volumeSeek)
        volumeValue = findViewById(R.id.volumeValue)
        ringtoneName = findViewById(R.id.ringtoneName)
        statusText = findViewById(R.id.statusText)

        mockContentInput.imeOptions = EditorInfo.IME_ACTION_DONE
        volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveRule(showToast = false)
                setStatus("音量已更新")
            }
        })

        findViewById<Button>(R.id.testButton).setOnClickListener {
            runMockSmsTest()
        }
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveRule(showToast = true)
        }
        findViewById<Button>(R.id.ringtoneButton).setOnClickListener {
            openRingtonePicker()
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            RingtoneService.stop(this)
            setStatus("已停止播放")
        }
    }

    private fun loadRule() {
        val rule = store.load()
        phoneInput.setText(rule.phoneKeyword)
        contentInput.setText(rule.contentKeyword)
        selectedRingtoneUri = rule.ringtoneUri
        volumeSeek.progress = (rule.volume * 100).toInt()
        volumeValue.text = "${volumeSeek.progress}%"
        updateRingtoneName()
        setStatus("填写号码或短信内容关键词后保存")
    }

    private fun saveRule(showToast: Boolean) {
        store.save(
            phoneKeyword = phoneInput.text.toString(),
            contentKeyword = contentInput.text.toString(),
            ringtoneUri = selectedRingtoneUri,
            volume = volumeSeek.progress / 100f
        )
        if (showToast) {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            setStatus("规则已保存")
        }
    }

    private fun runMockSmsTest() {
        saveRule(showToast = false)
        val rule = store.load()
        val mockSender = mockPhoneInput.text.toString()
        val mockMessage = mockContentInput.text.toString()

        if (rule.matches(mockSender, mockMessage)) {
            RingtoneService.start(this)
            setStatus("模拟短信匹配成功，正在播放")
        } else {
            RingtoneService.stop(this)
            setStatus("模拟短信未匹配规则")
        }
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提醒铃声")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
        }
        startActivityForResult(intent, REQUEST_RINGTONE)
    }

    private fun updateRingtoneName() {
        val title = try {
            RingtoneManager.getRingtone(this, selectedRingtoneUri)?.getTitle(this)
        } catch (_: Exception) {
            null
        }
        ringtoneName.text = title?.let { "当前铃声：$it" } ?: "当前铃声：系统默认"
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.RECEIVE_SMS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }

    companion object {
        private const val REQUEST_RINGTONE = 20
        private const val REQUEST_PERMISSIONS = 21
    }
}
