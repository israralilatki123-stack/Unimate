package com.example.unimate.ui.gpa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.GradeScaleItem
import com.example.unimate.data.model.SemesterCalculatedMetrics
import com.example.unimate.data.model.SemesterCourseGrade
import com.example.unimate.data.model.SemesterEntryMode
import com.example.unimate.data.model.SemesterGpaRecord
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateEmerald
import com.example.unimate.ui.theme.UniMatePurplePrimary
import java.util.Locale

@Composable
fun GpaCgpaSectionContent(
    uiState: GpaCgpaUiState,
    actionMessage: String?,
    onClearActionMessage: () -> Unit,
    onAddSemesterClick: (SemesterEntryMode) -> Unit,
    onEditSemesterClick: (SemesterGpaRecord) -> Unit,
    onDeleteSemesterClick: (String) -> Unit,
    onUpdateCourseGrade: (SemesterGpaRecord, String, String) -> Unit,
    onOpenGradingScaleDialog: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("gpa_cgpa_section_content"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (uiState) {
            GpaCgpaUiState.Loading -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("gpa_loading_state"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("Loading GPA & CGPA records…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            is GpaCgpaUiState.Error -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .testTag("gpa_error_state"),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AuthStatusBanner(message = uiState.message, type = AuthBannerType.ERROR)
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Retry Loading GPA Records")
                            }
                        }
                    }
                }
            }

            is GpaCgpaUiState.Empty -> {
                item {
                    GradingScaleNoticeBanner(
                        settings = uiState.settings,
                        onOpenGradingScaleDialog = onOpenGradingScaleDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
                if (!actionMessage.isNullOrBlank()) {
                    item {
                        ActionBannerBlock(
                            message = actionMessage,
                            onDismiss = onClearActionMessage,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }
                item {
                    GpaEmptyCard(
                        enrolledCoursesCount = uiState.enrolledCourses.size,
                        onAddCurrentSemester = { onAddSemesterClick(SemesterEntryMode.COURSES) },
                        onAddPreviousSemester = { onAddSemesterClick(SemesterEntryMode.SUMMARY) },
                        onConfigureScale = onOpenGradingScaleDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }

            is GpaCgpaUiState.Success -> {
                item {
                    GradingScaleNoticeBanner(
                        settings = uiState.settings,
                        onOpenGradingScaleDialog = onOpenGradingScaleDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                if (!actionMessage.isNullOrBlank()) {
                    item {
                        ActionBannerBlock(
                            message = actionMessage,
                            onDismiss = onClearActionMessage,
                            modifier = Modifier.widthIn(max = 760.dp),
                        )
                    }
                }

                item {
                    CgpaHeroSummaryCard(
                        uiState = uiState,
                        onAddCurrentSemester = { onAddSemesterClick(SemesterEntryMode.COURSES) },
                        onAddPreviousSemester = { onAddSemesterClick(SemesterEntryMode.SUMMARY) },
                        onConfigureScale = onOpenGradingScaleDialog,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                item {
                    GpaProgressChartCard(
                        semesterMetrics = uiState.summary.semesterMetrics,
                        maxScalePoints = uiState.summary.maxScalePoints,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }

                item {
                    Text(
                        text = "Semester Records (${uiState.summary.semesterMetrics.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                    )
                }

                items(
                    uiState.summary.semesterMetrics.reversed(),
                    key = { "sem-gpa-${it.semester.id}" },
                ) { metric ->
                    SemesterRecordCard(
                        metric = metric,
                        gradingScale = uiState.settings.gradingScale,
                        onEditClick = { onEditSemesterClick(metric.semester) },
                        onDeleteClick = { onDeleteSemesterClick(metric.semester.id) },
                        onChangeCourseGrade = { entryId, letter ->
                            onUpdateCourseGrade(metric.semester, entryId, letter)
                        },
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBannerBlock(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AuthStatusBanner(message = message, type = AuthBannerType.INFO)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun GradingScaleNoticeBanner(
    settings: AcademicSettings,
    onOpenGradingScaleDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = UniMateBlueElectric,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scale: ${settings.scaleName}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Configurable template — not an official university or Sukkur IBA grading policy until customized to your transcript.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onOpenGradingScaleDialog,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gpa_scale_banner_edit_button"),
            ) {
                Text("Edit")
            }
        }
    }
}

@Composable
private fun GpaEmptyCard(
    enrolledCoursesCount: Int,
    onAddCurrentSemester: () -> Unit,
    onAddPreviousSemester: () -> Unit,
    onConfigureScale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gpa_empty_state"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Calculate,
                contentDescription = null,
                tint = UniMatePurplePrimary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "Start Tracking Semester GPA & CGPA",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = if (enrolledCoursesCount > 0) {
                    "You have $enrolledCoursesCount enrolled course(s) ready to import, or record previous semester GPAs to plot cumulative CGPA progress."
                } else {
                    "Add courses with credit hours and letter grades, plus previous semester records, to calculate GPA & CGPA."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddCurrentSemester,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("gpa_empty_add_semester_button"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Semester Courses")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onAddPreviousSemester,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("gpa_empty_add_previous_button"),
                ) {
                    Text("Past Semester")
                }
                OutlinedButton(
                    onClick = onConfigureScale,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("gpa_empty_configure_scale_button"),
                ) {
                    Text("Grading Scale")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CgpaHeroSummaryCard(
    uiState: GpaCgpaUiState.Success,
    onAddCurrentSemester: () -> Unit,
    onAddPreviousSemester: () -> Unit,
    onConfigureScale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = uiState.summary
    val active = summary.activeSemesterMetrics

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gpa_hero_summary_card"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cumulative CGPA",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", summary.totalCredits)} total credit hrs • " +
                            "${String.format(Locale.US, "%.2f", summary.totalQualityPoints)} quality pts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.2f", summary.cgpa),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = UniMatePurplePrimary,
                    modifier = Modifier.testTag("cgpa_value_text"),
                )
            }

            if (active != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = UniMateEmerald.copy(alpha = 0.10f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${active.semester.semesterName} GPA (${String.format(Locale.US, "%.1f", active.totalCredits)} Cr)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = UniMateEmerald,
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", active.semesterGpa),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = UniMateEmerald,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAddCurrentSemester,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gpa_add_semester_courses_button"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Semester")
                }
                OutlinedButton(
                    onClick = onAddPreviousSemester,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gpa_add_previous_record_button"),
                ) {
                    Icon(Icons.Rounded.HistoryEdu, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Past Totals")
                }
                OutlinedButton(
                    onClick = onConfigureScale,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gpa_configure_scale_button"),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scale")
                }
            }
        }
    }
}

@Composable
private fun SemesterRecordCard(
    metric: SemesterCalculatedMetrics,
    gradingScale: List<GradeScaleItem>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onChangeCourseGrade: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = metric.semester
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("semester_gpa_card_${record.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = record.semesterName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        if (record.activeSemester) {
                            Surface(
                                color = UniMatePurplePrimary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "Current",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = UniMatePurplePrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "${String.format(Locale.US, "%.1f", metric.totalCredits)} Cr • " +
                            "Sem GPA ${String.format(Locale.US, "%.2f", metric.semesterGpa)} • " +
                            "Running CGPA ${String.format(Locale.US, "%.2f", metric.cumulativeCgpaAfter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${record.semesterName}")
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${record.semesterName}")
                    }
                }
            }

            if (record.resolvedEntryMode() == SemesterEntryMode.COURSES && record.courses.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                record.courses.forEach { course ->
                    SemesterCourseGradeRow(
                        course = course,
                        gradingScale = gradingScale,
                        onSelectGrade = { letter -> onChangeCourseGrade(course.id, letter) },
                    )
                }
            } else {
                Text(
                    text = "Recorded via semester summary (${String.format(Locale.US, "%.1f", metric.totalCredits)} credit hours @ ${String.format(Locale.US, "%.2f", metric.semesterGpa)} GPA).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SemesterCourseGradeRow(
    course: SemesterCourseGrade,
    gradingScale: List<GradeScaleItem>,
    onSelectGrade: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (course.courseCode.isNotBlank()) {
                    "${course.courseCode} • ${course.courseName}"
                } else {
                    course.courseName
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${String.format(Locale.US, "%.1f", course.creditHours)} Cr • ${String.format(Locale.US, "%.2f", course.gradePoints)} pts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(course.letterGrade, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                gradingScale.forEach { scaleItem ->
                    DropdownMenuItem(
                        text = {
                            Text("${scaleItem.letter} (${String.format(Locale.US, "%.2f", scaleItem.points)})")
                        },
                        onClick = {
                            expanded = false
                            onSelectGrade(scaleItem.letter)
                        },
                    )
                }
            }
        }
    }
}
