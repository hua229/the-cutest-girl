package com.thecutestgirl.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------- v4.1 浅色品牌体系（简洁白 + 粉红）----------------
// 页面底为带一丝暖粉的简洁白；卡面为 78% 白玻璃 + 1dp 半透边框 + 顶部内发光 + 柔和中性投影。
// 真实背景模糊只保留给底部液态导航栏；其余卡面共享同一套玻璃「外观」。

val CanvasLight = Color(0xFFEFE8EB)       // 页面底：带暖粉的浅灰，卡片对比度 ≈ 1.19:1
val CardWhite = Color(0xFFFFFFFF)         // 卡片实底
val GlassTint = Color(0xE6FFFFFF)         // 90% 白：玻璃态填充（叠加在卡底上）
val BorderHairline = Color(0x14201A1D)    // 8% 深：1dp 半透边框
val DividerSoft = Color(0x14201A1D)       // 8% 深：发丝分割线

val TextPrimary = Color(0xFF201A1D)       // 主文字（对比度 ≈ 15.6:1）
val TextSecondary = Color(0xFF625A5F)     // 次文字（≈ 6.9:1）
val TextMuted = Color(0xFF7E757C)         // 辅助（≈ 4.9:1）

val Pink = Color(0xFFC92661)              // 品牌粉：主操作 / 选中（白底 ≈ 5.3:1）
val PinkInk = Color(0xFF7A1038)           // 深粉：粉底上的文字
val PinkSoft = Color(0xFFFCE4EC)          // 粉容器 / 选中底
val PinkMist = Color(0xFFFDF2F6)          // 极淡粉：光晕 / 空态底

val MutedRose = Color(0xFF8A6572)         // 次强调（少量使用）
val EmberWarm = Color(0xFF9A5B00)         // 余烬橙降级为「警告」语义色

val SuccessGreen = Color(0xFF2E7D5B)
val WarningAmber = Color(0xFF9A5B00)
val DangerRed = Color(0xFFC62828)
val SuccessDeep = Color(0xFFE6F4EC)
val WarningDeep = Color(0xFFFDF3E3)
val DangerDeep = Color(0xFFFDECEC)

val OutlineNeutral = Color(0xFFE6DEE2)
val ScrimBlack = Color(0x66000000)        // 40% 黑：抽屉遮罩

// 兼容旧引用（v4.0 深色组件里少量名字），统一映射到浅色色板
val Void = CanvasLight
val Panel = CardWhite
val PanelRaised = Color(0xFFF5EFF2)
val OutlineDark = OutlineNeutral
val Plum = Pink
val PlumDeep = PinkSoft
val Cobalt = MutedRose
val CobaltDeep = Color(0xFFF3EAEE)
val Ember = Pink
val EmberDeep = PinkMist
val PrimaryLight = Pink
val PrimaryDark = Pink