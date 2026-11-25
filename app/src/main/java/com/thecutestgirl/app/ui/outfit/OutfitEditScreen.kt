package com.thecutestgirl.app.ui.outfit

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.ExperimentalMaterial3Api
import com.thecutestgirl.app.ui.components.ArtworkIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.ui.components.AppDatePickerDialog
import com.thecutestgirl.app.ui.components.AppTopBar
import com.thecutestgirl.app.ui.components.ConfirmDeleteDialog
import com.thecutestgirl.app.ui.components.DestructiveAction
import com.thecutestgirl.app.ui.components.PhotoNoteCard
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.toCnFull
import com.thecutestgirl.app.ui.components.toWeekday

/** 添加 / 编辑穿搭：选照片 + 日期 + 备注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitEditScreen(id: String?, onDone: () -> Unit) {
    val vm: OutfitEditViewModel = viewModel()
    LaunchedEffect(id) { id?.let(vm::load) }

    var showDatePicker by remember { mutableStateOf(false) }
    var hasChanges by rememberSaveable(id) { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (hasChanges) showDiscard = true else onDone()
    }
    BackHandler(onBack = requestBack)
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃修改？") },
            text = { Text("未保存的内容将被丢弃。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onDone()
                }) { Text("放弃修改", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("继续编辑") } }
        )
    }

    var showDelete by remember { mutableStateOf(false) }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = vm.date,
            onPick = { vm.date = it; hasChanges = true; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
    ConfirmDeleteDialog(
        visible = showDelete,
        onConfirm = { showDelete = false; vm.delete(onDone) },
        onDismiss = { showDelete = false }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "编辑穿搭",
                icon = Icons.Outlined.Checkroom,
                onBack = requestBack
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 照片 + 备注（上传人写）+ 评论（对方写）合成一张卡片。
            // allowRemove = false：穿搭记录必须保留一张照片。
            PhotoNoteCard(
                photos = listOfNotNull(vm.photoPath),
                onPhotosChanged = { vm.photoPath = it.firstOrNull(); hasChanges = true },
                note = vm.note,
                onNoteChange = { vm.note = it; hasChanges = true },
                noteLabel = "备注",
                allowRemove = false,
                noteEditable = vm.amUploader,
                noteAuthorName = vm.noteAuthorName,
                replyAuthorName = vm.replyAuthorName,
                reply = vm.reply,
                onReplyChange = { vm.reply = it; hasChanges = true },
                replyEditable = !vm.amUploader,
                replyLabel = "评论",
                // 对方进来只是评论，不该能换掉上传人的照片
                photoEditable = vm.amUploader
            )

            // 日期选择（FIX-04：对方只读，保存只写评论）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .solidCard(cornerRadius = 16.dp)
                    .clickable(enabled = vm.amUploader, role = Role.Button) {
                        if (vm.amUploader) showDatePicker = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkIcon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${vm.date.toCnFull()} ${vm.date.toWeekday()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (vm.amUploader) {
                    Text(
                        "修改",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (id != null && vm.canDelete) {
                DestructiveAction(
                    text = "删除这条穿搭",
                    onClick = { showDelete = true }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
