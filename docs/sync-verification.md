# 昵称与双人备注回归验证

版本：2.9.2（versionCode 33），2026-11-07。v2.9.2 做「换云盘自动重置同步账本」：设置页「测试并开启同步」检测到地址/账号变化时，清掉 `dirsReady`、文件指纹、已确认集合、已通知 id 与首次同步标记，并对新云盘当场重发 MKCOL 建出 `thecutestgirl/{records,photos}`（原来 `testConnection()` 只测账号根、必通过，是假成功；`dirsReady` 不重置会跳过建目录导致同步 404）。回归用例 24 → 26 项全部通过（证据 `.verification/tests-v292.txt`）。上一轮 v2.9.1（versionCode 32，2026-11-06）修「设置 → 后台不被冻结 → 去设置」点击无效（补 Manifest 权限 + `<queries>` 包可见性 + 三级回退跳转，证据 `.verification/tests-v291.txt`）；v2.9.0（versionCode 31，2026-11-03）做同步提速：云端记录按「服务器时间:字节数」指纹增量跳过、photos 目录只 PROPFIND 一次、前台 20 秒轮询、通知升到 HIGH 渠道并按记录去重、点通知直达对应 tab 与日期（证据 `.verification/tests-v290.txt`）。首版记录对应 2.4.2（versionCode 11），2026-10-26。

## 范围和结果

使用 API 35 模拟器、独立包名 `com.thecutestgirl.app.verification`、本地 WebDAV 服务，执行真实 Room、SyncEngine、编辑 ViewModel 和 WorkManager 调度。测试不使用真实坚果云账号，也不清理正式 APP 的数据库。

当前 26 项全部通过：

1. 穿搭备注从对方传回创建者，再传回对方，归属不变。
2. 日常点滴备注双向传播。
3. 纪念日备注双向传播。
4. 同一分钟内的上传人改名会被接收。
5. 改名不会把云端的新备注覆盖成旧内容。
6. 旧云端记录不回滚本地较新内容。
7. 本地尚未上传的新备注得到保留并上传。
8. 无变化的同步不重复写入或重复发通知。
9. 删除墓碑防止记录复活。
10. 纪念日编辑器确实保存输入的备注。
11. 上传期间再保存的内容保持待上传，并在下一轮上传。
12. 穿搭编辑版本高于从时钟较快的对方收到的版本。
13. 饮食编辑同样处理时钟偏差（v2.5.1 新增）。
14. 事件编辑同样处理时钟偏差。
15. 云端日期非法的记录只被跳过，不阻塞其它记录上传（v2.5.1 新增）。
16. 服务端始终拒收的一条记录不会拖住其它记录上传（v2.5.1 新增）。
17. 已有任务因失败而等待重试时，新的立即同步可以实际上传。

v2.8.0 新增（备注 / 评论双区）：

18. 上传人备注与对方评论在同一轮同步里同时保留——按记录级 `updatedAt` 裁决会丢掉其中一句，
    现在按 `noteUpdatedAt` / `replyUpdatedAt` 逐字段比较，两边都留下。
19. 对方只写评论时，上传人的备注不被空值顶掉，且评论署名（`replyName`）是写评论的那个人，
    不是上传人昵称。
20. 上传正在进行时对方写了评论，评论不因「记录已经传过」而丢失，且最终到达云端。

v2.8.3 新增（备注归上传人、评论归对方）：

21. 对方上传的记录里，我在编辑器里改备注不会被保存——用真实编辑 ViewModel 加载后程序性改写
    `vm.note` 再保存，落库的 `note` 仍是上传人原文，我写的内容只进 `reply`，`origin` 不变。
    这条同时锁住界面只读与保存路径两道门。
22. 备注区标签跟着上传人走：对方的记录显示「X 的备注」，自己的记录不贴标签（`noteAuthorName` 为空）。

v2.9.0 新增（同步提速，两项都是靠服务器侧的 GET 日志判定的）：

23. 指纹还没「确认稳定」时必须照常下载。增量同步用「服务器时间 + 字节数」判断文件变没变，
    可这两者都可能恰好不变（同一秒内改两次、改完长度又回到原样），所以同一个指纹要连续见到
    两次才敢跳过。这条用例清空 `file_confirmed` 后断言引擎必须再发一次 GET，
    再断言下一轮又恢复跳过。**注意不能用 `Result.downloaded` 判**——那个值只在
    「真的合并了新内容」时才加一，重新下载一份内容没变的文件仍然是 0。
24. 没变过的记录不该被反复下载。造 5 条云端记录 → 首轮下 5 条 → 再两轮把指纹确认稳定 →
    第三轮 0 条 → 只改其中 1 条 → 第四轮只下 1 条且内容更新 → 再两轮回到 0。
    这是「对方要好几分钟才看到」的直接原因：原来每轮把云端每个 JSON 都 GET 一遍。

v2.9.2 新增（换云盘自动重置账本）：

25. 换 WebDAV 端点后账本必须重置、目录必须自动建出。先在旧云盘跑两轮把
    `dirsReady` / `file_signatures` / `confirmedFiles` / `notifiedIds` / `firstSyncDone` 全部做实，
    再换一个随机新 URL（只建账号根，**故意不建 `thecutestgirl`**），调 `SyncEngine.applyDavConfig(...)`
    断言返回 true 且五个状态全清；`testConnection(prepareDirectories = changed)` 返回 true 后
    新云盘上 `thecutestgirl/records` 与 `photos` 都要存在、`dirsReady` 重新为 true；
    最后放一条新记录，确认能正常同步到本地（不再 404）。
26. 同一端点重存不清账本。用完全相同的 url / user / pass 调 `applyDavConfig` 断言 `!changed` 且
    五个状态都还在；再用前后带空格的 `"  $currentUrl  "` / `"  test  "` 重存断言 `!whitespace`
    —— `davConfigChanged` 的归一化必须和 `saveDavConfig` 完全一致，否则点一次「测试并开启同步」
    就会把本机增量账本白清一遍。

核心 10 项测试在旧实现上为 4/10，通过修复达到 10/10；后几类边界问题也先复现失败，再验证通过。v2.5.1 的三项新增用例同样先确认旧实现下的失败路径（饮食版本不递增 / 坏记录抛异常 / 上传中断整轮），再验证通过。当前（v2.9.2）`assembleDebug lintDebug` 构建成功，Lint **0 errors / 6 warnings**（6 条为项目原有警告：`ExifInterface`、`ModifierParameter`、`ObsoleteSdkInt`×2、`MonochromeLauncherIcon`×2）。v2.9.0 曾短暂多 1 条 `BatteryLife`（电池白名单入口），v2.9.1 已用 `tools:ignore` + `@Suppress` 压掉；v2.9.2 的 `resetForNewEndpoint` 用 `commit()` 触发的 `ApplySharedPref` 也已 `@Suppress`，保持 6 条基线。

已用中文测试记录核对穿搭上传人昵称、穿搭备注、纪念日备注及日常点滴备注的界面显示，并检查截图。最终 APK 的包名和签名与模拟器中原 v2.4.1 一致，versionCode 从 10 增至 16，可覆盖安装。

## 复跑

PowerShell 中使用项目的 JDK 和 Gradle 缓存：

```powershell
$env:JAVA_HOME = 'E:\appdate\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME = 'E:\appdate\gradle-home'
$env:ANDROID_USER_HOME = "$PWD\.android"
```

在另一个终端启动只监听本机的测试服务器：

```powershell
python tools/sync_test_server.py --root .verification/dav
```

模拟器须已启动且网络可用。首次设置 `captive_portal_mode 0` 后重连网络，并等待 `dumpsys connectivity` 出现 `VALIDATED`；不要在数据网络刚切换时立刻跑测试。

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest -PverificationApp --offline
$adb = 'E:\appdate\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r app/build/outputs/apk/debug/the_cutest_girl_v2.9.2_debug.apk
& $adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5554 shell am instrument -w com.thecutestgirl.app.verification.test/com.thecutestgirl.app.sync.SyncRegressionRunner
```

使用 Android 自带的 Instrumentation 入口，不额外下载测试框架。测试会重置**验证包**中的记录与设置，并在入口检查包名。必须检查输出的 `26/26 tests passed`，不能只看 adb 的退出码。改了 `Records.kt` 字段后构建要加 `--rerun-tasks`，否则 androidTest APK 不重编，运行期报 `NoSuchMethodError`。

## 正式交付包

测试后重新构建时**去掉** `-PverificationApp`：

```powershell
.\gradlew.bat assembleDebug lintDebug --offline
```

首次运行 Lint 若提示缺少缓存依赖，去掉 `--offline` 下载后再检查。交付前核对 `app/build/outputs/apk/debug/output-metadata.json` 的 applicationId 是 `com.thecutestgirl.app`，不是 `.verification`。

APK：`app/build/outputs/apk/debug/the_cutest_girl_v2.9.2_debug.apk`（19,355,069 字节，versionCode 33，applicationId `com.thecutestgirl.app`）。

两台手机都覆盖安装，不卸载。安装后先让修改方完成一次同步，再让接收方同步。v2.9.0 起 App 开着时每 20 秒轮询一次，退到后台后仍受 Android 15 分钟最小周期限制（这不是实时推送，要秒级必须走 FCM + 实名）。首次升级后到设置页点一下「后台不被冻结 → 去设置」把 App 加进电池白名单，否则国产 ROM 的省电策略会把后台同步拖到几十分钟——**v2.9.1 已修好这个按钮**：会弹出系统确认框，点 ALLOW 后设置页显示「已加入白名单」。

**v2.9.2 换云盘说明**：改地址 / 账号后点「测试并开启同步」，App 会自动清掉本机增量账本并在新云盘建好 `thecutestgirl/{records,photos}`，不用再手工建文件夹；手机上的记录和照片不会丢。但老记录已标「同步过」，不会重传到新云盘——要让对方看到历史，需先在两台手机各同步一次，再把旧云盘整个 `thecutestgirl/` 复制到新云盘同一级，最后两台都改配置并同步。此前已经被旧版清空且未保存的纪念日备注无法由本次更新恢复。

## 本轮证据

- `.verification/tests-before.txt`：旧代码复现。
- `.verification/tests-core-after.txt`：核心 10 项通过。
- `.verification/tests-edge-before.txt`：并发保存与时钟偏差复现。
- `.verification/tests-scheduler-before.txt`：重试阻塞复现。
- `.verification/tests-after.txt`：v2.4.2 时点最终 14 项通过。
- `.verification/tests-after-3fixes.txt`：v2.5.1 时点最终 17 项通过。
- `.verification/tests-v280.txt`：v2.8.0 时点 20 项通过。
- `.verification/tests-v281.txt`：v2.8.1 时点 20 项通过。
- `.verification/tests-v282.txt`：v2.8.2 时点 22 项通过。
- `.verification/tests-v283.txt`：v2.8.3 时点 22 项通过。
- `.verification/tests-v290.txt`：v2.9.0 时点 24 项通过。
- `.verification/tests-v291.txt`：v2.9.1 时点 24 项通过（补 Manifest 权限后同步逻辑未动，回归确认）。
- `.verification/tests-v292.txt`：**v2.9.2 时点 26 项通过**（新增换端点重置账本、同一端点保留账本两项）。
- `.verification/screens/v291-battery-dialog.png`：v2.9.1 点「去设置」弹出的系统电池优化确认框。
- `.verification/screens/v291-settings-whitelisted.png`：加入白名单后设置页显示「已加入白名单」。
- `.verification/screens/v292-settings-initial.png`：v2.9.2 换云盘前设置页（地址 `/notify-test/`，账本里 5 条指纹）。
- `.verification/screens/v292-settings-switched.png`：改地址到 `/switch-a/` 并点「测试并开启同步」后的设置页；同轮核对 `switch-a/thecutestgirl/{records,photos}` 已自动建出、`sync_state.xml` 指纹与通知 id 已清空、本地 6+2+2 条记录与 2 张照片仍在。
- `.verification/final-build.log`：最终构建与 Lint。
- `.verification/screens/`：界面截图及 UI 层级记录。
- `.verification/crash-log.txt`：模拟器 crash 缓冲日志。

本轮没有连接两台真实手机或用户的坚果云账号；验证使用模拟器和本地 WebDAV。
