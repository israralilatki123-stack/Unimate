package com.example.unimate.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    isMutating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, courseName: String, category: TaskCategory, dueDateText: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var courseName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TaskCategory.ASSIGNMENT) }
    var dueDateText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        title = { Text("Add Assignment, Quiz or Exam") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = errorMessage, type = AuthBannerType.ERROR)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Lab 4 Trees Report") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_title_input"),
                )
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Code / Name") },
                    placeholder = { Text("e.g. CS-301") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_course_input"),
                )
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("Due Date / Time") },
                    placeholder = { Text("e.g. Friday • 11:59 PM") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_due_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, courseName, category, dueDateText) },
                enabled = !isMutating,
                modifier = Modifier.testTag("add_task_save_button"),
            ) {
                if (isMutating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}

@Composable
fun AddNoteDialog(
    isMutating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, courseName: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var courseName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        title = { Text("Create Study Note") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = errorMessage, type = AuthBannerType.ERROR)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_note_title_input"),
                )
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course") },
                    placeholder = { Text("e.g. CS-301") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Study Notes") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, courseName, content) },
                enabled = !isMutating,
                modifier = Modifier.testTag("add_note_save_button"),
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}

@Composable
fun AddReminderDialog(
    isMutating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, remindAtText: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var remindAtText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        title = { Text("Add Reminder") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = errorMessage, type = AuthBannerType.ERROR)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder") },
                    placeholder = { Text("e.g. Submit assignment on LMS") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = remindAtText,
                    onValueChange = { remindAtText = it },
                    label = { Text("When") },
                    placeholder = { Text("e.g. Today • 8:00 PM") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, remindAtText) },
                enabled = !isMutating,
            ) {
                Text("Save Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}

@Composable
fun AddGoalDialog(
    isMutating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, targetSteps: Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var stepsText by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        title = { Text("Create Study Goal") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthStatusBanner(message = errorMessage, type = AuthBannerType.ERROR)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal Title") },
                    placeholder = { Text("e.g. Solve 5 past papers") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stepsText,
                    onValueChange = { stepsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Target Steps (1–500)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, stepsText.toLongOrNull() ?: 0L) },
                enabled = !isMutating,
            ) {
                Text("Create Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}
