package com.thecutestgirl.app.ui.meal

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.MEAL_NAMES
import com.thecutestgirl.app.ui.components.ConfirmDeleteDialog
import com.thecutestgirl.app.ui.components.quietTopAppBar
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
            TopAppBar(
                modifier = Modifier.quietTopAppBar(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MealDuotoneIcon(mealType = mealType)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "记录${MEAL_NAMES[mealType]}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save(day, mealType, onDone) },
                        enabled = vm.canSave
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 照片 + 备注（上传人写）+ 评论（对方写）合并成一张连贯卡片
            PhotoNoteCard(
                photos = listOfNotNull(vm.photoPath),
                onPhotosChanged = { vm.photoPath = it.firstOrNull() },
                note = vm.note,
                onNoteChange = { vm.note = it },
                notePlaceholder = "这一餐吃了什么？比如：和男朋友吃的火锅，超满足",
                noteEditable = vm.amUploader,
                noteAuthorName = vm.noteAuthorName,
                replyAuthorName = vm.replyAuthorName,
                reply = vm.reply,
                onReplyChange = { vm.reply = it },
                replyEditable = !vm.amUploader,
                replyPlaceholder = "说点什么…比如：下次带我去",
                photoEditable = vm.amUploader
            )

            if (recordId != null && vm.canDelete) {
                OutlinedButton(
                    onClick = { showDelete = true },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
