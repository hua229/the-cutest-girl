# the cutest girl 🩷

记录女朋友的穿搭、三餐和日常小事的 Android App，给两个人用。

- 界面：Jetpack Compose（Material 3，浅色品牌主题：简洁白 + 粉红，玻璃卡面）
- 数据：Room 数据库；数据默认只在本机，开启同步后两台手机通过私有坚果云 WebDAV 目录互相同步
- 最低支持 Android 8.0（minSdk 26）
- 当前版本：`4.1.0`（versionCode 40）

## 功能

| 底部导航 | 功能 |
| --- | --- |
| 穿搭 | 按月浏览每日穿搭照片，照片 + 日期 + 备注 |
| 饮食 | 按天记录早 / 午 / 晚三餐，照片 + 文字；可以给对方写评论 |
| 事件 | 纪念日自动算「第 N 天 / 还有 N 天」，日常点滴可配图文 |
| 设置 | 昵称、坚果云 WebDAV 同步、电池优化白名单、导出备份 |

## 同步与权限

两台手机都填**同一个坚果云账号**并开启同步：

- 记录：`/thecutestgirl/records/<id>.json`
- 删除墓碑：`/thecutestgirl/records/T_<id>.json`
- 照片：`/thecutestgirl/photos/<name>.jpg`

需要这些系统权限：

- `INTERNET`：访问坚果云 WebDAV
- `POST_NOTIFICATIONS`：对方有新内容时提醒（Android 13+）
- `RECEIVE_BOOT_COMPLETED`：开机后恢复后台同步任务
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：设置页「后台不被冻结」入口

应用密码使用 Android Keystore 加密后保存在本机，不写入备份、不打印日志。换手机或卸载重装后需要重新填一次应用密码。

## 构建

要求 **JDK 17**（Gradle 8.9 不支持过新的 JDK）。

`gradle.properties` 里的 `org.gradle.java.home=E:/appdate/jdk-17.0.20.1+1` 是**本机专用路径**；换电脑时改成自己的 JDK 17 路径，或删掉该行并在命令行设置 `JAVA_HOME`：

```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:GRADLE_USER_HOME = "C:\path\to\gradle-home"   # 可选
./gradlew.bat :app:assembleDebug --offline
```

产物：`app/build/outputs/apk/debug/the_cutest_girl_v<版本>_debug.apk`。两台手机覆盖安装（debug 签名一致，数据保留）。

## 测试

```bash
# 纯函数单元测试（照片列表、日期解析、同步签名）
./gradlew.bat :app:testDebugUnitTest --offline

# 编译 + 静态检查
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

设备回归（29 项，Room + 真实 WebDAV 夹具 + ViewModel）：

```bash
# 1) 起本地测试服务器
python tools/sync_test_server.py --port 18765 --root .verification/dav

# 2) 构建验证变体（applicationId 会加 .verification，绝不碰正式包数据）
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PverificationApp --offline

# 3) 安装并运行
adb install -r -t app/build/outputs/apk/debug/the_cutest_girl_v4.1.0_debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner
```

跑完回归后记得用**不带** `-PverificationApp` 的命令重新出正式包。

## 项目结构

```
TheCutestGirl/
├── app/src/main/java/com/thecutestgirl/app/
│   ├── MainActivity.kt            # 入口：主题、通知落点、启动维护
│   ├── data/                      # Room 实体/DAO/迁移、照片、设置、备份导出
│   ├── sync/                      # WebDAV 客户端、同步引擎、WorkManager、状态/错误模型
│   └── ui/                        # AppRoot + 页面（穿搭/饮食/点滴/设置/编辑页）+ 组件 + 主题
├── app/src/test/                  # JVM 纯函数测试
├── app/src/androidTest/           # 设备回归（SyncRegressionRunner）
├── app/schemas/                   # Room schema（迁移对照）
├── tools/sync_test_server.py      # 本地 WebDAV 夹具
└── .verification/                 # 测试证据（截图、回归输出）
```

## 注意

- 不开启同步时，数据只在这台手机上；卸载 App 会一起删除。
- 需要备份时在「设置 → 数据备份 → 导出备份」导出 zip（数据库 + 照片，不含密码）。
- 详细设计、历史版本与不可违反的约束见 `AI_HANDOFF.md`。