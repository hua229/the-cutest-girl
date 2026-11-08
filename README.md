# the cutest girl 🩷

记录女朋友的穿搭、三餐和日常小事的 Android APP。

- 界面：Jetpack Compose（Material 3，浅色品牌主题：简洁白 + 粉红，玻璃卡面，固定浅色不跟随系统）
- 数据：Room 数据库，全部存在手机本地，不需要联网、不需要账号、不要任何权限
- 最低支持 Android 8.0

## 功能

| 底部导航 | 功能 |
| --- | --- |
| 穿搭 | 按月浏览每日穿搭照片，照片 + 日期 + 备注 |
| 饮食 | 按天记录早 / 午 / 晚三餐，照片 + 文字 |
| 事件 | 纪念日自动算「第 N 天 / 还有 N 天」，日常点滴可配图文 |

## 项目结构（不用记，给以后改代码时参考）

```
TheCutestGirl/
├── app/src/main/java/com/thecutestgirl/app/
│   ├── MainActivity.kt            # APP 入口
│   ├── data/                      # 数据层：数据库表、读写接口、照片文件管理
│   └── ui/
│       ├── AppRoot.kt             # 底部导航 + 页面路由
│       ├── theme/                 # 配色主题
│       ├── components/            # 通用小组件（日历、照片选择、删除确认）
│       ├── outfit/                # 穿搭页
│       ├── meal/                  # 饮食页
│       └── event/                 # 事件页
└── app/src/main/res/              # 图标、名称等资源
```

## 如何在 Android Studio 里打开（写给以后的自己）

1. 打开 Android Studio → File → Open → 选择 `TheCutestGirl` 文件夹
2. 首次打开会自动下载依赖，等右下角进度条走完
3. 点顶部绿色 ▶ 运行按钮，选一个模拟器或插着的手机即可

## 如何打包 APK 装到手机

- **最简单**：Android Studio 里 Build → Build APK(s)，完成后点右下角弹窗的 locate 找到
  `app/build/outputs/apk/debug/app-debug.apk`，发到手机上点击安装
- **或命令行**：项目目录下运行 `gradlew assembleDebug`（Windows）

## 注意

- 所有数据（含照片）只存在这台手机上，卸载 APP 会一起删掉
- 换手机或刷机前记得先别卸载……以后可以加「导出备份」功能
