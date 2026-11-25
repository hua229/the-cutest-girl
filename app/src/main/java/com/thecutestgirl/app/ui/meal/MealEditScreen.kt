package com.thecutestgirl.app.ui.meal

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.MEAL_NAMES
import com.thecutestgirl.app.ui.components.AppTopBar
import com.thecutestgirl.app.ui.components.ConfirmDeleteDialog
import com.thecutestgirl.app.ui.components.DestructiveAction
import com.thecutestgirl.app.ui.components.PhotoNoteCard
import java.time.LocalDate

/** 记录一餐：照片（可选）+ 文字 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditScreen(
    date: String,
    mealType: Int,
    recordId: String?,
    onDone: () -> Unit
) {
    val vm: MealEditViewModel = viewModel()
    val day = remember(date) {
        runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    }
    LaunchedEffect(recordId) { recordId?.let(vm::load) }

    var hasChanges by rememberSaveable(recordId) { mutableStateOf(false) }
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
                title = "记录${MEAL_NAMES[mealType]}",
                icon = mealIcon(mealType),
                onBack = requestBack
            ) {
                TextButton(
                    onClick = { vm.save(day, mealType, onDone) },
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
            // 照片 + 备注（上传人写）+ 评论（对方写）合并成一张连贯卡片
            PhotoNoteCard(
                photos = listOfNotNull(vm.photoPath),
                onPhotosChanged = { vm.photoPath = it.firstOrNull(); hasChanges = true },
                note = vm.note,
                onNoteChange = { vm.note = it; hasChanges = true },
                noteLabel = "内容",
                noteEditable = vm.amUploader,
                noteAuthorName = vm.noteAuthorName,
                replyAuthorName = vm.replyAuthorName,
                reply = vm.reply,
                onReplyChange = { vm.reply = it; hasChanges = true },
                replyEditable = !vm.amUploader,
                replyLabel = "评论",
                photoEditable = vm.amUploader,
                // 与饮食列表卡一致：3:2，避免列表/编辑页裁切跳变
                photoRatio = 3f / 2f
            )

            if (recordId != null && vm.canDelete) {
                DestructiveAction(
                    text = "删除这条记录",
                    onClick = { showDelete = true }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
