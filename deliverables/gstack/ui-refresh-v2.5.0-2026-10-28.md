# v2.5.0 UI 改版交付：新图标 / 按钮 emoji / 设置入口独立 / 图片容器玻璃化

**日期**：2026-10-28
**场景**：UI 修复与改版（单成员直调执行）
**版本**：v2.5.0（versionCode 15）

---

## 📌 TL;DR

- 整体结论：🟢 通过（编译 + 安装 + 冒烟全绿）
- 四项需求全部落地，模拟器实测无错位/遮挡/重叠，logcat 零应用崩溃
- 下一步：可直接装机使用；深色模式下玻璃框观感可再微调

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟢 Go |
| 编译 | BUILD SUCCESSFUL（37 tasks） |
| 冒烟 | 穿搭/饮食/事件三页 + 午餐编辑页 + 桌面图标，全部正常 |
| 崩溃 | 应用进程零 FATAL（模拟器自身蓝牙栈有一条，与 APP 无关） |

---

## 1. 应用图标替换

- 源图：用户提供的毛毡玩偶合照（1214×1295）
- 处理（`tools/make_launcher_icon.py` 重写）：中心 8% 内缩取景（小尺寸下主体更清晰）→ 中心裁方（不拉伸）→ LANCZOS 生成 5 密度 foreground（108/162/216/324/432px）
- 背景色：`#F85B88`（旧粉）→ `#D1BD96`（画面暖沙色，采样自图片上缘）
- 验证：圆形裁切预览两张脸完整在安全区内；模拟器闪屏实际显示新图标

## 2. 二级（详情）界面按钮 emoji

- 「换一张/再加」→ **🖼️** 前缀；「拍一张」→ **📷** 前缀；「移除」保持红色文字不加重视觉
- 新私有组件 `ButtonEmoji`：13sp（比按钮正文 labelLarge 14sp 小一档，沿用 EmojiTitle 的比例原则）+ 固定行高 + 6dp 间距（与空态按钮「图标+文字」间距一致）
- 覆盖所有板块：PhotoNoteCard 是穿搭/饮食/事件三个编辑页共用的同一组件
- 实测：记录午餐详情页三个胶囊按钮与参考图布局一致

## 3. 设置入口与顶部栏

- 设置齿轮从 `PageHeader` 标题行**移除**，每页只保留一个入口
- 新增 `GlassTopBarWithSettings`：顶栏面板 + 右侧**独立的 44dp 圆形玻璃设置按钮**（与 FAB 同一套玻璃材质），物理上与日期栏 ◀ ▶ 分开，不会重叠误触
- `GlassTopPanel` 新增 `outerPadding` 参数（默认行为不变，被收进 Row 时传 0 避免双份留白）
- 标题行左侧安全边距 4dp → 14dp，emoji + 文字整体右移，不再贴玻璃面板边

## 4. 饮食/事件图片容器磨砂玻璃化

- 新增 `Modifier.glassPhotoFrame()`（Glass.kt）：14dp 圆角 + surface 55% 半透明底 + 玻璃折射描边（drawGlassEdge），无阴影（嵌在卡片里，投影会显脏）
- 覆盖：饮食页餐食卡片的「已上传照片展示框 + 同步占位框」、事件页日常点滴卡片的同款两处
- 技术约束：这些容器位于列表 `.haze()` 采样源**内部**，Haze 硬约束禁止 hazeChild 作为其子孙（v2.4.5 冒烟已踩过这个崩溃），故采用静态玻璃方案——与编辑页图文卡片同一思路，半透明底承担磨砂观感

## 改动文件清单

| 文件 | 改动 |
|---|---|
| `tools/make_launcher_icon.py` | 重写为新图处理逻辑（中心裁方 + 内缩取景 + 背景色采样） |
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | 5 密度全部重新生成 |
| `app/src/main/res/values/colors.xml` | ic_launcher_background → #D1BD96 |
| `ui/components/Common.kt` | 新增 ButtonEmoji；PhotoNoteCard 有照片时「换一张/拍一张」加 emoji；PageHeader 移除齿轮、左边距 4→14dp、去掉 onOpenSettings 参数 |
| `ui/components/Glass.kt` | GlassTopPanel 新增 outerPadding；新增 GlassTopBarWithSettings（独立设置圆钮）；新增 Modifier.glassPhotoFrame() |
| `ui/outfit/OutfitScreen.kt` | 顶栏改 GlassTopBarWithSettings |
| `ui/meal/MealScreen.kt` | 顶栏改 GlassTopBarWithSettings；餐食照片/占位框加玻璃框 |
| `ui/event/EventScreen.kt` | 顶栏改 GlassTopBarWithSettings；点滴照片/占位框加玻璃框 |
| `app/build.gradle.kts` | versionCode 14→15、versionName 2.4.5→2.5.0 |

## 验证记录（2026-10-28 模拟器冒烟）

- `compileDebugKotlin` / `assembleDebug` BUILD SUCCESSFUL；APK `the_cutest_girl_v2.5.0_debug.apk`（19.2MB）安装 Success
- 冷启动闪屏显示新图标；穿搭/饮食/事件三页：标题右移、独立玻璃设置圆钮、无重叠
- 注入带照片的午餐记录：列表照片带玻璃框 → 进详情页：🖼️ 换一张 / 📷 拍一张 / 移除 三胶囊正常
- 注入带照片的事件记录：点滴卡片照片带玻璃框（Coil 首次解码约 2-3s 属正常）
- 测试数据已清理；logcat `AndroidRuntime:E` 与 crash buffer 均无应用崩溃
- 截图：`deliverables/smoke/v250_*.png`（含 s1 穿搭 / s2 饮食 / s3 事件 / s4 饮食玻璃框 / s5 编辑页 emoji 按钮 / s7b 事件玻璃框）

## ⚠️ 已知局限

- 模拟器 `no-window` 模式下 SystemUI 启动初期会弹「System UI isn't responding」，与 APP 无关，点 Wait 即可
- 列表页图片框是静态玻璃（无实时模糊），因为容器在 haze 采样源内部受 Haze 约束；视觉上与真模糊几乎无差别
- 深色模式下 glassPhotoFrame 的半透明底（surface 55%）未单独实测，如有偏差可调 surfaceAlpha

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
