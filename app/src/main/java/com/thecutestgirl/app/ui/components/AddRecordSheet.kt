package com.thecutestgirl.app.ui.components

import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.thecutestgirl.app.ui.theme.appPalette
import com.thecutestgirl.app.ui.theme.windowBackgroundArgb
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.SheetValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.ui.theme.TheCutestGirlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 统一的新增记录抽屉（v4.0）。
 *
 * 规范：
 * - 半屏（约 0.55 屏高），顶部圆角 24，玻璃面板
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
    saving: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentSaving by rememberUpdatedState(saving)
    val currentUnsaved by rememberUpdatedState(hasUnsavedChanges)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && (currentSaving || currentUnsaved)) {
                if (!currentSaving) showDiscardConfirm = true
                false
            } else true
        }
    )
    val resumeEditing: () -> Unit = {
        showDiscardConfirm = false
        // Material3's Android back callback can reach onDismissRequest after the
        // sheet has hidden. Reuse the same composition and state so that fields,
        // scroll position and pending photo results remain intact.
        scope.launch { sheetState.show() }
    }
    ModalBottomSheet(
        onDismissRequest = {
            when {
                saving -> scope.launch { sheetState.show() }
                hasUnsavedChanges -> {
                    showDiscardConfirm = true
                    // The parent keeps this sheet until explicit discard.
                    scope.launch { sheetState.show() }
                }
                else -> onDismiss()
            }
        },
        sheetState = sheetState,
        scrimColor = appPalette.scrim,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        MatchDialogSystemBars()
        AddRecordSheetContent(
            title = title,
            primaryEnabled = primaryEnabled && !saving,
            onPrimary = onPrimary,
            primaryLabel = if (saving) "正在保存…" else primaryLabel,
            content = content
        )
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = resumeEditing,
            title = {
                MatchDialogSystemBars()
                Text("放弃这次编辑？")
            },
            text = { Text("已填写的内容和已选的照片不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = resumeEditing) { Text("继续编辑") }
            }
        )
    }
}

/** Dialog windows do not inherit the Activity's manually selected bar mode. */
@Composable
internal fun MatchDialogSystemBars() {
    val view = LocalView.current
    val dark = appPalette.isDark
    DisposableEffect(view, dark) {
        val applyBars = Runnable {
            findDialogWindow(view)?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = windowBackgroundArgb(dark)
                if (Build.VERSION.SDK_INT >= 29) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        applyBars.run()
        // Apply after Material3's Dialog parameter update too; remove stale posts
        // when the window leaves composition or its appearance mode changes.
        view.post(applyBars)
        onDispose { view.removeCallbacks(applyBars) }
    }
}

private fun findDialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

/** 抽屉内容（与 ModalBottomSheet 解耦，方便 @Preview 预览开 / 合两种状态）。 */
@Composable
fun AddRecordSheetContent(
    title: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    primaryLabel: String = "保存",
    content: @Composable ColumnScope.() -> Unit
) {
    // 在 IME 已占用的可用区域内计算高度，不用最小高度反向撑出屏幕。
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().imePadding()) {
        val sheetHeight = (maxHeight * 0.86f).coerceAtMost(720.dp)
        Column(
            modifier = Modifier.fillMaxWidth().height(sheetHeight)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f))
            )
            Spacer(Modifier.height(16.dp))
            // 标题也参与滚动，为小屏、横屏与大字体保留表单和固定保存区。
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                content()
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = primaryLabel, onClick = onPrimary, enabled = primaryEnabled)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 新增与全屏编辑共享字段结构及可访问名称。 */
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) = RecordTextField(label, value, onValueChange, modifier, singleLine, minLines, keyboardType)

/** 抽屉日期行：只读展示；[onClick] 为 null 时是不可点的信息行。 */
@Composable
fun SheetDateRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            ArtworkIcon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** 抽屉分段选择（日常点滴 / 纪念日）。 */
@Composable
fun SheetSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) scheme.surface else Color.Transparent)
                    .selectable(selected = selected,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

private val sheetCameraCompression = Mutex()

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
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val currentPhotos by rememberUpdatedState(photos)
    val addPhotos by rememberUpdatedState(onAdd)

    // 统一收口：抽屉里的照片都是未保存草稿，替换/超额的旧文件当场删掉，避免孤儿
    fun deliver(names: List<String>) {
        val remaining = (maxPhotos - currentPhotos.size).coerceAtLeast(0)
        val accepted = if (maxPhotos == 1) names.takeLast(1) else names.take(remaining)
        names.filter { it !in accepted }.forEach { PhotoStore.delete(context, it) }
        if (accepted.isEmpty()) return
        if (maxPhotos == 1) {
            currentPhotos.filter { it !in accepted }.forEach { PhotoStore.delete(context, it) }
        }
        addPhotos(accepted)
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraTarget?.let { name ->
            if (ok) capturedPhoto = name else PhotoStore.delete(context, name)
        }
        cameraTarget = null
    }
    // 保留相机回传的草稿名，压缩期间发生重建也能继续处理同一个文件。
    LaunchedEffect(capturedPhoto) {
        val name = capturedPhoto ?: return@LaunchedEffect
        val file = PhotoStore.file(context, name)
        if (file.isFile && file.length() > 0) {
            withContext(Dispatchers.IO) {
                sheetCameraCompression.withLock { PhotoStore.compressInPlace(file) }
            }
            if (name !in currentPhotos) deliver(listOf(name))
        } else {
            PhotoStore.delete(context, name)
        }
        capturedPhoto = null
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
                val remaining = (maxPhotos - currentPhotos.size).coerceAtLeast(0)
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
        val (file, uri) = PhotoStore.newCameraTarget(context, draft = true)
        cameraTarget = file.name
        takePicture.launch(uri)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { name ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                                .padding(2.dp)
                                .size(48.dp)
                                .clickable { onRemove(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center) {
                                ArtworkIcon(Icons.Outlined.Close, contentDescription = "移除照片",
                                    tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        val canAdd = photos.size < maxPhotos && cameraTarget == null && capturedPhoto == null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftCapsuleButton(
                onClick = launchGallery,
                modifier = Modifier.weight(1f),
                enabled = canAdd,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                ArtworkIcon(
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
                ArtworkIcon(
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
                    label = "内容",
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
                SheetSegmented(
                    options = listOf("日常点滴", "纪念日"),
                    selectedIndex = 0,
                    onSelect = {}
                )
                SheetTextField(
                    value = "一起去看了日落",
                    onValueChange = {},
                    label = "标题"
                )
                SheetDateRow(label = "发生日期", value = "2026年8月24日", onClick = {})
            }
        )
    }
}
