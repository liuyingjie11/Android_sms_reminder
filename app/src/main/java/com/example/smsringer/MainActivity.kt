package com.example.smsringer

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
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
    private lateinit var vibrateSwitch: Switch
    private lateinit var hideFromRecentsSwitch: Switch
    private lateinit var smsObserverSwitch: Switch
    private lateinit var ringtoneName: TextView
    private lateinit var statusText: TextView
    private var selectedRingtoneUri: Uri = Settings.System.DEFAULT_NOTIFICATION_URI
    private var isLoadingRule = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = RuleStore(this)
        RingtoneService.ensurePlaybackNotificationChannel(this)
        bindViews()
        loadRule()
        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        showLastSmsDiagnostic()
    }

    override fun onPause() {
        super.onPause()
        saveRule(showToast = false)
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
        if (requestCode == REQUEST_PERMISSIONS) {
            setStatus(buildPermissionStatus())
        }
        if (requestCode == REQUEST_READ_SMS) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                SmsObserverService.ensureObserverNotificationChannel(this)
                SmsObserverService.start(this)
                smsObserverSwitch.isChecked = true
                setStatus("后台短信监听已开启")
            } else {
                smsObserverSwitch.isChecked = false
                setStatus("未授予短信读取权限，无法启用后台监听")
            }
        }
    }

    private fun bindViews() {
        phoneInput = findViewById(R.id.phoneInput)
        contentInput = findViewById(R.id.contentInput)
        mockPhoneInput = findViewById(R.id.mockPhoneInput)
        mockContentInput = findViewById(R.id.mockContentInput)
        volumeSeek = findViewById(R.id.volumeSeek)
        volumeValue = findViewById(R.id.volumeValue)
        vibrateSwitch = findViewById(R.id.vibrateSwitch)
        hideFromRecentsSwitch = findViewById(R.id.hideFromRecentsSwitch)
        smsObserverSwitch = findViewById(R.id.smsObserverSwitch)
        ringtoneName = findViewById(R.id.ringtoneName)
        statusText = findViewById(R.id.statusText)

        mockContentInput.imeOptions = EditorInfo.IME_ACTION_DONE

        val autoSaveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isLoadingRule) return
                saveRule(showToast = false)
            }
        }
        phoneInput.addTextChangedListener(autoSaveWatcher)
        contentInput.addTextChangedListener(autoSaveWatcher)

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
        vibrateSwitch.setOnCheckedChangeListener { _, _ ->
            if (isLoadingRule) return@setOnCheckedChangeListener
            saveRule(showToast = false)
            setStatus("震动设置已更新")
        }
        hideFromRecentsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingRule) return@setOnCheckedChangeListener
            saveRule(showToast = false)
            applyHideFromRecents(isChecked)
            setStatus(if (isChecked) "已从最近任务中隐藏" else "已在最近任务中显示")
        }
        smsObserverSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingRule) return@setOnCheckedChangeListener
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
                    smsObserverSwitch.isChecked = false
                    setStatus("需要短信读取权限才能启用后台监听")
                    return@setOnCheckedChangeListener
                }
                try {
                    SmsObserverService.ensureObserverNotificationChannel(this)
                    SmsObserverService.start(this)
                    setStatus("后台短信监听已开启")
                } catch (e: Exception) {
                    smsObserverSwitch.isChecked = false
                    setStatus("启动监听失败：${e.javaClass.simpleName}")
                }
            } else {
                SmsObserverService.stop(this)
                setStatus("后台短信监听已关闭")
            }
        }

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
        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            requestSmsPermissionManually()
        }
        findViewById<Button>(R.id.notificationPermissionButton).setOnClickListener {
            requestNotificationPermissionManually()
        }
    }

    private fun loadRule() {
        val rule = store.load()
        isLoadingRule = true
        phoneInput.setText(rule.phoneKeyword)
        contentInput.setText(rule.contentKeyword)
        selectedRingtoneUri = rule.ringtoneUri
        volumeSeek.progress = (rule.volume * 100).toInt()
        volumeValue.text = "${volumeSeek.progress}%"
        vibrateSwitch.isChecked = rule.vibrateWhenPlaying
        hideFromRecentsSwitch.isChecked = rule.hideFromRecents
        smsObserverSwitch.isChecked = SmsObserverService.isRunning(this)
        isLoadingRule = false
        applyHideFromRecents(rule.hideFromRecents)
        updateRingtoneName()
        setStatus("填写号码或短信内容关键词后保存")
    }

    private fun saveRule(showToast: Boolean) {
        store.save(
            phoneKeyword = phoneInput.text.toString(),
            contentKeyword = contentInput.text.toString(),
            ringtoneUri = selectedRingtoneUri,
            volume = volumeSeek.progress / 100f,
            vibrateWhenPlaying = vibrateSwitch.isChecked,
            hideFromRecents = hideFromRecentsSwitch.isChecked
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

        val matched = rule.matches(mockSender, mockMessage)
        val detail = "号码关键词=[${rule.phoneKeyword}] 内容关键词=[${rule.contentKeyword}] 测试号码=[$mockSender] 测试内容=[${mockMessage.take(30)}]"
        if (matched) {
            RingtoneService.start(this)
            if (areNotificationsAvailable()) {
                setStatus("匹配成功，正在播放")
            } else {
                setStatus("正在播放；请开启通知权限后才能在通知栏停止")
            }
        } else {
            RingtoneService.stop(this)
            setStatus("未匹配；$detail")
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
        if (!hasSmsPermission()) {
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

    private fun requestSmsPermissionManually() {
        if (hasSmsPermission()) {
            setStatus("短信权限已开启")
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), REQUEST_PERMISSIONS)
        setStatus("请在弹窗中允许短信权限")
    }

    private fun hasSmsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermissionManually() {
        RingtoneService.ensurePlaybackNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_PERMISSIONS)
            setStatus("请在弹窗中允许通知权限")
            return
        }

        if (areNotificationsAvailable()) {
            setStatus("通知权限已开启")
        } else {
            openAppNotificationSettings()
            setStatus("请在系统页面允许通知")
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun areNotificationsAvailable(): Boolean {
        if (!hasNotificationPermission()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(RingtoneService.CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun buildPermissionStatus(): String {
        val smsStatus = if (hasSmsPermission()) "短信权限已开启" else "短信权限未开启"
        val notificationStatus = if (areNotificationsAvailable()) "通知权限已开启" else "通知权限未开启"
        return "$smsStatus，$notificationStatus"
    }

    private fun showLastSmsDiagnostic() {
        val lastStatus = SmsDiagnostics(this).loadStatus()
        if (lastStatus.isNotBlank()) {
            setStatus("最近真实短信：$lastStatus")
        }
    }

    private fun applyHideFromRecents(hide: Boolean) {
        val activityManager = getSystemService(ActivityManager::class.java)
        try {
            activityManager.appTasks.forEach { task ->
                task.setExcludeFromRecents(hide)
            }
        } catch (_: Exception) {
            setStatus("最近任务显示设置未生效")
        }
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }

    companion object {
        private const val REQUEST_RINGTONE = 20
        private const val REQUEST_PERMISSIONS = 21
        private const val REQUEST_READ_SMS = 22
    }
}
