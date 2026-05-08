# 短信铃声提醒

一个个人使用的 Android 短信提醒工具。App 可以根据短信号码或短信内容关键词进行匹配，命中规则后循环播放自定义铃声，并支持在 App 页面或系统通知中停止播放。

## 功能

- 匹配手机号：支持多个关键词，用英文逗号 `,` 或中文逗号 `，` 分隔。
- 匹配短信内容：支持多个关键词，用英文逗号 `,` 或中文逗号 `，` 分隔。
- 模拟短信测试：填写模拟号码和模拟内容后，可验证当前规则是否会触发铃声。
- 自定义铃声：通过系统铃声选择器选择提醒音。
- 音量设置：使用 App 内部播放音量，不直接修改系统音量。
- 播放时震动：可独立开启或关闭。
- 停止播放：支持页面按钮停止，也支持播放通知中的停止按钮。
- 后台短信监听：用于小米等无法稳定收到短信广播的设备，开启后会以前台服务方式监听短信数据库变化。
- 最近任务隐藏：可控制 App 是否从最近任务列表中隐藏。

## 匹配规则

只要满足以下任意条件，就会触发铃声：

- 短信发件号码包含「匹配手机号」中的任一关键词。
- 短信正文包含「匹配短信内容」中的任一关键词。

空规则不会触发，避免收到任意短信都响。

示例：

```text
匹配手机号：95588，10086
匹配短信内容：验证码,到账,取件码
```

## 权限说明

App 使用以下权限：

- `RECEIVE_SMS`：接收系统短信广播。
- `READ_SMS`：后台短信监听功能读取最新短信。
- `POST_NOTIFICATIONS`：Android 13+ 显示播放通知和监听通知。
- `FOREGROUND_SERVICE`：播放铃声和后台短信监听需要前台服务。
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`：铃声播放前台服务。
- `FOREGROUND_SERVICE_DATA_SYNC`：后台短信监听前台服务。
- `VIBRATE`：播放时震动。
- `RECEIVE_BOOT_COMPLETED`：重启后恢复已开启的后台短信监听。

短信权限属于敏感权限，适合个人安装使用。如果未来要上架应用商店，需要按平台短信权限审核规则重新设计。

## 小米手机使用建议

部分小米 / MIUI / HyperOS 设备可能不会弹出短信权限窗口，或者不给第三方 App 稳定的短信广播权限。可以按下面顺序排查：

1. 在 App 中点击「申请短信权限」和「申请通知权限」。
2. 到系统设置中手动检查 App 权限，确认短信、通知权限已允许。
3. 如果短信广播仍不触发，开启 App 内的「后台短信监听」。
4. 给 App 设置后台运行相关权限，例如自启动、后台弹出界面、后台运行无限制、电池优化无限制等。

注意：后台短信监听使用前台服务实现，Android 要求前台服务必须显示通知。关闭该监听后，App 会主动移除监听通知。

## 构建

环境要求：

- JDK 17
- Android SDK
- Gradle Wrapper

构建 debug 包：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装

连接 Android 手机并开启 USB 调试后执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果需要通过 adb 手动授予权限：

```bash
adb shell pm grant com.example.smsringer android.permission.RECEIVE_SMS
adb shell pm grant com.example.smsringer android.permission.READ_SMS
adb shell pm grant com.example.smsringer android.permission.POST_NOTIFICATIONS
```

其中 `POST_NOTIFICATIONS` 只适用于 Android 13 及以上。

## 页面说明

- 匹配规则：设置真实短信的号码和内容关键词。
- 模拟短信：输入模拟号码、模拟内容，点击「模拟测试」验证规则。
- 提醒设置：选择铃声、停止播放、调节音量、设置播放时震动。
- 系统设置：申请权限、控制最近任务隐藏、开启或关闭后台短信监听。

## 常见问题

### 模拟测试能响，真实短信不响

通常说明规则和铃声播放逻辑没有问题，问题集中在短信接收通道。请检查：

- `RECEIVE_SMS` 是否已授权。
- 小米手机是否拦截了短信权限申请。
- 是否需要开启「后台短信监听」。
- 是否设置了自启动和后台运行权限。

### 开启后台短信监听后为什么有通知

后台短信监听是前台服务，Android 系统要求前台服务显示通知。这个通知用于提示用户 App 正在后台运行，不能完全静默隐藏。

### 铃声通知不显示

请确认：

- 已授予通知权限。
- 系统设置中没有关闭本 App 通知。
- 通知渠道「短信铃声提醒」没有被设置为不允许。

### 规则怎么写多个关键词

用逗号分隔即可：

```text
验证码，取件码,到账
```

中英文逗号都支持。
