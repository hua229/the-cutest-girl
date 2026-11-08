package com.thecutestgirl.app.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.EVENT_TYPE_ANNIVERSARY
import com.thecutestgirl.app.data.EVENT_TYPE_MOMENT
import com.thecutestgirl.app.ui.components.AppDatePickerDialog
import com.thecutestgirl.app.ui.components.AppTopBar
import com.thecutestgirl.app.ui.components.ConfirmDeleteDialog
import com.thecutestgirl.app.ui.components.DestructiveAction
import com.thecutestgirl.app.ui.components.PhotoNoteCard
import com.thecutestgirl.app.ui.components.PlainField
import com.thecutestgirl.app.ui.components.CommentField
import com.thecutestgirl.app.ui.components.ReadOnlyNote
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.toCnFull
import com.thecutestgirl.app.ui.components.toWeekday

/** 添加 / 编辑事件：日常点滴（可配图）或纪念日（自动算天数） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(id: String?, onDone: () -> Unit) {
    val vm: EventEditViewModel = viewModel()
    LaunchedEffect(id) { id?.let(vm::load) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = vm.date,
            onPick = { vm.date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
    ConfirmDeleteDialog(
        visible = showDelete,
        onConfirm = { showDelete = false; vm.delete(onDone) },
        onDismiss = { showDelete = false }
    )

    val isMoment = vm.type == EVENT_TYPE_MOMENT

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = if (id == null) "记一条点滴" else "编辑点滴",
                icon = Icons.Outlined.FavoriteBorder,
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
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 类型选择（编辑时不允许换类型，避免数据错乱）
                if (id == null) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = isMoment,
                            onClick = { vm.type = EVENT_TYPE_MOMENT },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("日常点滴")
                        }
                        SegmentedButton(
                            selected = !isMoment,
                            onClick = { vm.type = EVENT_TYPE_ANNIVERSARY },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("纪念日")
                        }
                    }
                }

                PlainField(
                    value = vm.title,
                    onValueChange = { vm.title = it },
                    label = if (isMoment) "标题" else "纪念日名称",
                    placeholder = if (isMoment) "比如：一起去看了日落" else "比如：在一起"
                )

                // 只有日常点滴可以配照片；照片与描述合并成一张连贯卡片（v2.4.4 建立，v2.4.5 玻璃化）
                if (isMoment) {
                    PhotoNoteCard(
                        photos = listOfNotNull(vm.photoPath),
                        onPhotosChanged = { vm.photoPath = it.firstOrNull() },
                        note = vm.description,
                        onNoteChange = { vm.description = it },
                        notePlaceholder = "写点描述（可选），比如：在海边等到日落才肯走",
                        noteEditable = vm.amUploader,
                        noteAuthorName = vm.noteAuthorName,
                        replyAuthorName = vm.replyAuthorName,
                        reply = vm.reply,
                        onReplyChange = { vm.reply = it },
                        replyEditable = !vm.amUploader,
                        replyPlaceholder = "说点什么…比如：这张拍得真好",
                        photoEditable = vm.amUploader,
                        // 与点滴列表卡一致：3:2
                        photoRatio = 3f / 2f
                    )
                } else {
                    // 纪念日没有照片；备注归上传人，评论归对方
                    if (vm.amUploader) {
                        PlainField(
                            value = vm.description,
                            onValueChange = { vm.description = it },
                            label = "备注（可选）",
                            placeholder = "想记点什么就写在这里",
                            singleLine = false,
                            minLines = 3
                        )
                    } else if (vm.description.isNotBlank()) {
                        ReadOnlyNote(
                            label = vm.noteAuthorName.takeIf { it.isNotBlank() }?.let { "$it 的备注" },
                            value = vm.description
                        )
                    }
                    CommentField(
                        value = vm.reply,
                        onValueChange = { vm.reply = it },
                        editable = !vm.amUploader,
                        placeholder = "说点什么…比如：今天也要一起过"
                    )
                }

                // 日期选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .solidCard(cornerRadius = 16.dp)
                        .clickable { showDatePicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${if (isMoment) "发生日期" else "开始日期"}：" +
                            "${vm.date.toCnFull()} ${vm.date.toWeekday()}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "修改",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (id != null && vm.canDelete) {
                    DestructiveAction(
                        text = "删除这条点滴",
                        onClick = { showDelete = true }
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
