package com.example.unimate.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.data.model.AcademicTask
import com.example.unimate.data.model.GpaCourseEntry
import com.example.unimate.data.model.LetterGrade
import com.example.unimate.data.model.StudentReminder
import com.example.unimate.data.model.TaskCategory
import com.example.unimate.data.model.UserProfile
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.theme.UniMateAmber
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary
import java.util.Locale

@Composable
fun GpaCalculatorDialog(
    entries: List<GpaCourseEntry>,
    calculatedGpa: Double,
    totalCredits: Double,
    errorMessage: String?,
    onAddCourse: (courseName: String, creditHours: Double, grade: LetterGrade) -> Unit,
    onRemoveCourse: (String) -> Unit,
    onUpdateGrade: (String, LetterGrade) -> Unit,
    onDismiss: () -> Unit,
) {
    var courseName by remember { mutableStateOf("") }
    var creditsText by remember { mutableStateOf("3") }
    var selectedGrade by remember { mutableStateOf(LetterGrade.A) }
    var newGradeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gpa_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = UniMatePurplePrimary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Calculated GPA",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", totalCredits)} total credits",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f", calculatedGpa),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = UniMatePurplePrimary,
                            modifier = Modifier.testTag("gpa_dialog_result_value"),
                        )
                    }
                }

                AuthStatusBanner(message = errorMessage, type = AuthBannerType.ERROR)

                entries.forEach { entry ->
                    var gradeMenuOpen by remember(entry.id) { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.courseName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = "${entry.creditHours} credits • ${entry.grade.points} pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                TextButton(onClick = { gradeMenuOpen = true }) {
                                    Text(entry.grade.label)
                                }
                                DropdownMenu(
                                    expanded = gradeMenuOpen,
                                    onDismissRequest = { gradeMenuOpen = false },
                                ) {
                                    LetterGrade.entries.forEach { g ->
                                        DropdownMenuItem(
                                            text = { Text("${g.label} (${g.points})") },
                                            onClick = {
                                                onUpdateGrade(entry.id, g)
                                                gradeMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onRemoveCourse(entry.id) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove course")
                            }
                        }
                    }
                }

                Text(
                    text = "Add Course",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gpa_course_name_input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = creditsText,
                        onValueChange = { creditsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Credits") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        OutlinedButton(
                            onClick = { newGradeExpanded = true },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text("Grade: ${selectedGrade.label}")
                        }
                        DropdownMenu(
                            expanded = newGradeExpanded,
                            onDismissRequest = { newGradeExpanded = false },
                        ) {
                            LetterGrade.entries.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text("${g.label} (${g.points})") },
                                    onClick = {
                                        selectedGrade = g
                                        newGradeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val credits = creditsText.toDoubleOrNull() ?: 0.0
                        onAddCourse(courseName, credits, selectedGrade)
                        if (courseName.isNotBlank() && credits > 0.0) {
                            courseName = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("gpa_add_course_button"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to GPA")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

@Composable
fun NotificationsDialog(
    reminders: List<StudentReminder>,
    tasks: List<AcademicTask>,
    isDemo: Boolean,
    onToggleReminderDone: (StudentReminder) -> Unit,
    onAddReminderClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activeAssessments = tasks.filter { !it.completed && it.resolvedCategory() != TaskCategory.ASSIGNMENT }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.notifications_title))
                if (isDemo) DemoBadgePill()
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (reminders.isEmpty() && activeAssessments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notifications_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                reminders.forEach { reminder ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onToggleReminderDone(reminder) }) {
                                Icon(
                                    imageVector = if (reminder.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = "Toggle reminder",
                                    tint = if (reminder.done) UniMateEmerald else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reminder.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = reminder.remindAtText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                activeAssessments.forEach { assessment ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = UniMateAmber.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, UniMateAmber.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${assessment.resolvedCategory().label}: ${assessment.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "${assessment.courseName} • ${assessment.dueDateText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onAddReminderClick()
                },
            ) {
                Text("New Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAccountDialog(
    profile: UserProfile,
    isGmailVerified: Boolean,
    isPhoneVerified: Boolean,
    isSigningOut: Boolean,
    onEditProfileClick: () -> Unit,
    onVerifyPhoneClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Student Profile & Account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = profile.fullName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = profile.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isGmailVerified) {
                        StatusChip(stringResource(R.string.dashboard_badge_gmail_verified))
                    }
                    if (isPhoneVerified) {
                        StatusChip(stringResource(R.string.dashboard_badge_phone_verified))
                    }
                }
                Text(
                    text = "University: ${profile.university}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Department: ${profile.department}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Semester: ${profile.semester}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (profile.phoneNumber.isNotBlank()) {
                    Text(
                        text = "Phone: ${profile.phoneNumber}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = {
                        onDismiss()
                        onEditProfileClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_edit_profile_button"),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_edit_profile))
                }

                if (!isPhoneVerified) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onVerifyPhoneClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_verify_phone_button"),
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dashboard_verify_phone_cta))
                    }
                }

                OutlinedButton(
                    onClick = onSignOutClick,
                    enabled = !isSigningOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSigningOut) stringResource(R.string.dashboard_signing_out)
                        else stringResource(R.string.dashboard_sign_out),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

@Composable
private fun StatusChip(label: String) {
    Surface(
        color = UniMateEmerald.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = UniMateEmerald,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = UniMateEmerald,
            )
        }
    }
}
