package com.thecutestgirl.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.ui.theme.LocalAppPalette
import com.thecutestgirl.app.ui.theme.TheCutestGirlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一的新增记录抽屉（v4.0）。
 *
 * 规范：
 * - 半屏（约 0.55 屏高），顶部圆角 20，玻璃面板
 * - 40% 黑色遮罩，点击遮罩 / 下滑关闭
 * - 进出一个 spring（约 300ms），遮罩同步淡入淡出（Material3 Sheet 默认行为）
 * - 内容 8dp 网格；主操作 PrimaryButton 全宽 48dp
 * - 只负责表单 UI 与回调，写入仍走原 ViewModel / Room
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    primaryLabel: String = "保存",
    hasUnsavedChanges: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiscardConfirm by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = {
            if (hasUnsavedChanges) showDiscardConfirm = true else onDismiss()
        },
        sheetState = sheetState,
        // 遮罩统一取 palette（两套主题都是 40% 黑），和设置抽屉同一个来源
        scrimColor = LocalAppPalette.current.scrim,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        AddRecordSheetContent(
            title = title,
            primaryEnabled = primaryEnabled,
            onPrimary = onPrimary,
            primaryLabel = primaryLabel,
            onCancel = onDismiss,
            content = content
        )
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃这次编辑？") },
            text = { Text("已填写的内容和已选的照片不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") }
            }
        )
    }
}

/** 抽屉内容（与 ModalBottomSheet 解耦，方便 @Preview 预览开 / 合两种状态）。 */
@Composable
fun AddRecordSheetContent(
    title: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    primaryLabel: String = "保存",
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val topOnly = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().imePadding()) {
        // 键盘弹起时按 IME 之上的可用高度重算：无键盘约 82% 且 ≥360dp；
        // IME 态 360dp 下限失效、兜底 240dp；可用高不足 240dp 时切同一 Sheet 的全屏表单态。
        val imeBottom = WindowInsets.ime.getBottom(density)
        val imeHeight = with(density) { imeBottom.toDp() }
        val keyboardVisible = imeHeight > 0.dp
        val availableHeight = maxHeight - imeHeight
        val fullForm = keyboardVisible && availableHeight < 240.dp
        val desiredHeight = if (keyboardVisible) {
            minOf(maxHeight * 0.82f, availableHeight)
        } else {
            maxHeight * 0.82f
        }
        val sheetHeight = if (fullForm) {
            maxHeight
        } else {
            desiredHeight.coerceAtLeast(if (keyboardVisible) 240.dp else 360.dp)
                .coerceAtMost(maxHeight)
        }
        val sheetShape = if (fullForm) RoundedCornerShape(0.dp) else topOnly
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                // 抽屉必须完全不透明：半透明会让底层文字/图标透上来，读不清
                .glassPanel(shape = sheetShape, elevated = true)
                .padding(24.dp)
        ) {
        if (fullForm) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onCancel?.invoke() }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onPrimary, enabled = primaryEnabled) {
                    Text("保存")
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            // 拖动指示条（装饰，本身不占点击）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f))
            )
            Spacer(Modifier.height(24.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
        Spacer(Modifier.height(16.dp))
        if (!fullForm) {
            PrimaryButton(
                text = primaryLabel,
                onClick = onPrimary,
                enabled = primaryEnabled
            )
            Spacer(Modifier.height(8.dp))
        }
        }
    }
}

/** 抽屉里的字段标题（14sp 辅助色）。 */
@Composable
fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 抽屉文本输入：深色输入底 + 8dp 圆角 + 无边框输入。 */
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    val hairline = palette.hairline
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(scheme.surfaceVariant)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) scheme.primary else hairline,
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.mutedText
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 抽屉日期行：只读展示；[onClick] 为 null 时是不可点的信息行。 */
@Composable
fun SheetDateRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, LocalAppPalette.current.hairline, shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressTint(interactionSource, cornerRadius = 10.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 全 app 唯一的分段选择控件（v4.3.0 从 `SheetSegmented` 改名：它早就不只属于抽屉，
 * 饮食 / 点滴 / 外观页都在用）。选中项中性实色底 + 主色实色描边，未选中 surfaceVariant；
 * 按压反馈走 [pressTint]，和行类一致。
 */
@Composable
fun SegmentedChoices(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val hairline = LocalAppPalette.current.hairline
    val slot = LocalAppPalette.current.segmentSlot
    val selectedSurface = LocalAppPalette.current.elevatedSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(slot)
            .border(1.dp, hairline, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) selectedSurface else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .pressTint(interactionSource, cornerRadius = 10.dp)
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** 抽屉照片选择：缩略图 + 相册 / 拍照。 */
@Composable
fun SheetPhotoPicker(
    photos: List<String>,
    maxPhotos: Int,
    onAdd: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraTarget by remember { mutableStateOf<File?>(null) }

    // 统一收口：抽屉里的照片都是未保存草稿，替换/超额的旧文件当场删掉，避免孤儿
    fun deliver(names: List<String>) {
        val remaining = (maxPhotos - photos.size).coerceAtLeast(1)
        val accepted = if (maxPhotos == 1) names.takeLast(1) else names.take(remaining)
        if (accepted.isEmpty()) {
            names.forEach { PhotoStore.delete(context, it) }
            return
        }
        if (maxPhotos == 1) {
            photos.filter { it !in accepted }.forEach { PhotoStore.delete(context, it) }
        }
        onAdd(accepted)
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraTarget
        if (ok && file != null && file.exists() && file.length() > 0) {
            scope.launch {
                withContext(Dispatchers.IO) { PhotoStore.compressInPlace(file) }
                deliver(listOf(PhotoStore.makeDraft(context, file)))
            }
        } else {
            file?.delete()
        }
    }
    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { PhotoStore.saveFromUri(context, uri, draft = true)?.let { deliver(listOf(it)) } }
        }
    }
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxPhotos.coerceAtLeast(2))
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                // 先按剩余名额截断再复制，避免多余文件落盘
                val remaining = (maxPhotos - photos.size).coerceAtLeast(1)
                val saved = uris.take(remaining).mapNotNull { PhotoStore.saveFromUri(context, it, draft = true) }
                deliver(saved)
            }
        }
    }
    val launchGallery: () -> Unit = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (maxPhotos > 1) {
            val launched = runCatching { pickMultiple.launch(request) }.isSuccess
            if (!launched) pickSingle.launch(request)
        } else {
            pickSingle.launch(request)
        }
    }
    val launchCamera: () -> Unit = {
        val (file, uri) = PhotoStore.newCameraTarget(context)
        cameraTarget = file
        takePicture.launch(uri)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { name ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        PhotoImage(
                            file = PhotoStore.file(context, name),
                            contentDescription = "照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(0.dp)
                                .size(48.dp)
                                .clickable { onRemove(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    // 压在照片上的移除键：恒定深底白图标，不跟主题翻，
                                    // 否则深色主题下「深圆 + 浅图标」会在浅照片上消失
                                    .background(Color.Black.copy(alpha = 0.55f))
                            )
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "移除照片",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        val canAdd = photos.size < maxPhotos
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftCapsuleButton(
                onClick = launchGallery,
                modifier = Modifier.weight(1f),
                enabled = canAdd,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (maxPhotos > 1) "加照片" else "选照片",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            SoftCapsuleButton(
                onClick = launchCamera,
                modifier = Modifier.weight(1f),
                enabled = canAdd,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "拍一张",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (photos.size >= maxPhotos) {
            Text(
                "最多 $maxPhotos 张",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- 预览（fake 数据，不涉及凭证 / Room）----------------

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F8)
@Composable
private fun AddRecordSheetContentPreview() {
    TheCutestGirlTheme {
        AddRecordSheetContent(
            title = "记录午餐",
            primaryEnabled = true,
            onPrimary = {},
            content = {
                SheetPhotoPicker(
                    photos = emptyList(),
                    maxPhotos = 1,
                    onAdd = {},
                    onRemove = {}
                )
                SheetTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "吃了什么（可选）",
                    minLines = 3,
                    singleLine = false
                )
            }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F8)
@Composable
private fun AddRecordSheetFilledPreview() {
    TheCutestGirlTheme {
        AddRecordSheetContent(
            title = "记一条点滴",
            primaryEnabled = false,
            onPrimary = {},
            primaryLabel = "保存",
            content = {
                SegmentedChoices(
                    options = listOf("日常点滴", "纪念日"),
                    selectedIndex = 0,
                    onSelect = {}
                )
                SheetTextField(
                    value = "一起去看了日落",
                    onValueChange = {},
                    placeholder = "标题"
                )
                SheetDateRow(label = "发生日期", value = "2026年8月24日", onClick = {})
            }
        )
    }
}
