# the cutest girl 🩷

记录女朋友的穿搭、三餐和日常小事的 Android App，给两个人用。

- 界面：Jetpack Compose（Material 3，克制的 iOS 风格、低饱和玫粉点缀、浅深主题与悬浮玻璃导航）
- 数据：Room 数据库；数据默认只在本机，开启同步后两台手机通过私有坚果云 WebDAV 目录互相同步
- 最低支持 Android 8.0（minSdk 26）
- 当前版本：`5.0.0`（versionCode 46）

## 功能

| 底部导航 | 功能 |
| --- | --- |
| 穿搭 | 按月浏览每日穿搭照片，照片 + 日期 + 备注 |
| 饮食 | 按天记录早 / 午 / 晚三餐，照片 + 文字；可以给对方写评论 |
| 点滴 | 纪念日自动算「第 N 天 / 还有 N 天」，日常点滴可配图文 |

三个主页右上角统一提供**设置**和**新增**。设置进入同一个分组首页，再进入五个独立模块；返回后回到原主页。底部保留「穿搭 / 饮食 / 点滴」三栏，图标下增加中文标签，悬浮胶囊支持点按及长按拖动切换。

| 设置模块（独立页面） | 内容 |
| --- | --- |
| 我的信息 | 昵称（会同步给对方） |
| 外观 | 跟随系统（默认）、浅色、深色；只保存在本机 |
| 云同步 | 坚果云 WebDAV 服务器、账号、应用密码、立即同步 |
| 数据备份 | 导出记录、照片和设备身份为 ZIP；新机可从备份恢复 |
| 后台不被冻结 | 加入电池优化白名单，避免后台同步被系统掐断 |

5.0.0 采用实色内容卡片，玻璃模糊保留给底部导航；主题切换保留当前页面与未保存内容，设置返回时恢复列表位置和页头留白。5.0.0 已通过 Kotlin / AndroidTest 编译、19 项 JVM 单测及独立模拟器 33 项完整设备回归；lint 为 0 错误 / 6 警告 / 2 信息，正常包构建与固定签名校验通过。发布产物证据见 `deliverables/release-v5.0.0.json`。41 项 UI 场景与截图是在升级版本号前、同一功能代码的 4.3.1（45）构建上完成，分阶段范围见 `deliverables/ui-refinement/validation.md`，设计说明见 `docs/design-system.md`。

新增操作使用统一生成的磨砂加号；页面去掉标语、示例和重复说明，新增与编辑复用有常驻字段名的输入组件。系统字体与字号缩放、真实作者、日期、错误及确认信息继续保留；输入语义同时供读屏与智能体定位。

13 套图标素材通过原生 `image_gen` 生成。用户指定 Image2，但工具未暴露实际模型 ID；relay 路径检查时没有 API 凭据，因此不作已核验模型的声明。

本版同时交付界面使用的 ZIP 备份恢复、部分同步状态（`Partial`）及相关回归支持；这些现有工作区功能与 UI 一起提交，字段归属、删除墓碑和照片草稿规则沿用当前实现。

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
$env:TCG_SIGNING_STORE_FILE = "C:\private\the-cutest-girl-debug.keystore"
./gradlew.bat :app:assembleDebug --offline
```

当前普通 debug 产物：`app/build/outputs/apk/debug/the_cutest_girl_v5.0.0_debug.apk`，文件名由 `versionName` 与构建变体动态生成。构建优先读取本机 `.signing/the-cutest-girl-debug.keystore`，也可通过上面的环境变量指定；密钥不提交到 Git。5.0.0 沿用 4.3.1 的固定证书 SHA-256：`b57546d5a5d3b7e4f395bded269c37ecb34cd7bc22cd9676d573588446221547`，不匹配时停止打包。

交付副本：`deliverables/the_cutest_girl_v5.0.0.apk`，包名 `com.thecutestgirl.app`，版本 `5.0.0` / `46`；已通过 APK Signature Scheme v2 校验。文件 SHA-256：`b4d9e6ce46adbff0efa24152357679674ad59d275f37fedc740664a3ab01da9d`。

4.3.1 使用新固定签名：旧版原签名密钥已无法找到，因此旧签名用户首次升级前需要完整备份并迁移数据；不能仅靠提高版本号实现覆盖安装。已迁移到该固定证书的安装可继续覆盖升级至 5.0.0。需要独立安装的开发验证包时使用 `-PverificationApp`，它不替换日常应用。

## 测试

```bash
# 纯函数单元测试（照片列表、日期解析、同步签名、主题档位）
./gradlew.bat :app:testDebugUnitTest --offline

# 编译 + 静态检查
./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --offline
```

UI 回归使用独立包名 `com.thecutestgirl.app.verification.iosui` 与合成记录：

```powershell
./gradlew.bat :app:assembleDebug -PverificationApp -I tools/ios_ui_verification.init.gradle --offline
# 安装到测试模拟器 emulator-5556 后：
python tools/ios_ui_fixture.py --seed
python tools/ios_ui_refinement_qa.py
python tools/ios_ui_qa.py --groups settings themes navigation responsive --output .verification/ui-refinement/qa-system
python tools/ios_ui_state_qa.py --checks state gallery
```

验证报告和截图写入 `.verification/ui-refinement/` 的对应目录；脚本结束时恢复模拟器显示设置及外观，含合成记录的测试会核对内容哈希。`syncstate` 仅临时注入隔离包的模拟失败状态并还原，不访问真实云端。整理后的验收结果见 `deliverables/ui-refinement/validation.md`。

设备回归（33 项，Room + WebDAV 夹具 + ViewModel + 备份恢复）：

```bash
# 1) 起本地测试服务器
python tools/sync_test_server.py --port 18765 --root .verification/dav

# 2) 构建验证变体（applicationId 会加 .verification，绝不碰正式包数据）
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PverificationApp --offline

# 3) 安装并运行
adb install -r -t app/build/outputs/apk/debug/the_cutest_girl_v5.0.0_debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner
```

跑完回归后记得用**不带** `-PverificationApp` 的命令重新出正式包。

## 项目结构

```
TheCutestGirl/
├── app/src/main/java/com/thecutestgirl/app/
│   ├── MainActivity.kt            # 入口：主题、通知落点、启动维护
│   ├── data/                      # Room 实体/DAO/迁移、照片、设置、备份导出与恢复
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
- 需要备份时在任一主页右上角「设置」→「数据备份」→「导出备份」导出 zip（数据库 + 照片，不含密码）。
- 换机时，在空安装的「数据备份」页选择「从备份恢复」，核对记录和照片数量后确认。恢复完成后可重新配置云同步。
- 详细设计、历史版本与不可违反的约束见 `AI_HANDOFF.md`。
