package com.thecutestgirl.app.ui.outfit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thecutestgirl.app.data.OutfitRecord
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.ui.components.AppDatePickerDialog
import com.thecutestgirl.app.ui.components.AppTopBar
import com.thecutestgirl.app.ui.components.BarIconSize
import com.thecutestgirl.app.ui.components.ConfirmDeleteDialog
import com.thecutestgirl.app.ui.components.IconTitle
import com.thecutestgirl.app.ui.components.quietTopAppBar
import com.thecutestgirl.app.ui.components.PhotoNoteCard
import com.thecutestgirl.app.ui.components.PhotoSyncPlaceholder
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.toCnFull
import com.thecutestgirl.app.ui.components.toCnShort
import com.thecutestgirl.app.ui.components.toWeekday
import java.time.LocalDate

/**
 * 指定某一天的穿搭上传页（v2.4.4）：
 * - 顶部日期行决定照片归属，可改；改完下方「这一天的照片」立即跟着换
 * - 已有照片以卡片网格展示，点照片进单条编辑（改备注 / 换图 / 删除），右上角 ✕ 直接删除
 * - 新选的照片统一归到这一天，点保存时为每张照片各建一条记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitUploadScreen(
    dateArg: String,
    onOpen: (String) -> Unit,
    onDone: () -> Unit
) {
    val vm: OutfitUploadViewModel = viewModel()
    val date by vm.date.collectAsStateWithLifecycle()
    val existing by vm.existing.collectAsStateWithLifecycle()
    // 只允许删除本机上传的照片，对方上传的只读
    val uploadContext = LocalContext.current
    val myDeviceIdForUpload = remember(uploadContext) { SettingsStore.deviceId(uploadContext) }

    LaunchedEffect(dateArg) { vm.ensureDate(dateArg) }

    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OutfitRecord?>(null) }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = date,
            onPick = { vm.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
    pendingDelete?.let { record ->
        ConfirmDeleteDialog(
            visible = true,
            onConfirm = { vm.deleteExisting(record); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "记 ${date.toCnShort()} 的穿搭",
                icon = Icons.Outlined.Checkroom,
                onBack = onDone
            ) {
                TextButton(
                    onClick = { vm.save(onDone) },
                    enabled = vm.canSave
                ) {
                    Text("保存")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---------- 归属日期 ----------
                DatePickerRow(date = date, onClick = { showDatePicker = true })

                // ---------- 这一天已有的照片 ----------
                if (existing.isNotEmpty()) {
                    ExistingPhotosBlock(
                        records = existing,
                        myDeviceId = myDeviceIdForUpload,
                        onOpen = onOpen,
                        onDelete = { pendingDelete = it }
                    )
                }

                // ---------- 新增照片 + 备注（图文一体卡片，玻璃材质）----------
                Text(
                    if (existing.isEmpty()) "添加这一天的照片" else "再加几张",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
                PhotoNoteCard(
                    photos = vm.newPhotos,
                    onPhotosChanged = { vm.newPhotos = it },
                    note = vm.note,
                    onNoteChange = { vm.note = it },
                    notePlaceholder = "写点备注（可选），比如：奶白针织衫 + 牛仔半身裙",
                    maxPhotos = 9
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 归属日期行：日历图标 + 完整日期 + 右侧「修改」 */
@Composable
private fun DatePickerRow(date: LocalDate, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Event,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${date.toCnFull()} ${date.toWeekday()}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "修改",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary
        )
    }
}

/** 已有照片：两列网格，点进单条编辑，右上角 ✕ 删除 */
@Composable
private fun ExistingPhotosBlock(
    records: List<OutfitRecord>,
    myDeviceId: String,
    onOpen: (String) -> Unit,
    onDelete: (OutfitRecord) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            "这一天的照片 · ${records.size} 张",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            records.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { record ->
                        ExistingThumb(
                            record = record,
                            canDelete = record.origin.isBlank() ||
                                record.origin == "local" ||
                                record.origin == myDeviceId,
                            onClick = { onOpen(record.id) },
                            onDelete = { onDelete(record) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ExistingThumb(
    record: OutfitRecord,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // v2.5.2：和列表页 OutfitThumb 对齐——先判文件存在，缺图时给占位而不是空白框
    val photoFile = PhotoStore.file(context, record.photoPath)
    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .solidCard(cornerRadius = 16.dp, elevation = 6.dp)
            .clickable(onClick = onClick)
    ) {
        if (photoFile.exists()) {
            AsyncImage(
                model = photoFile,
                contentDescription = "穿搭照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PhotoSyncPlaceholder(
                failed = false,
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        if (canDelete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "删除这张照片",
                        tint = scheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
