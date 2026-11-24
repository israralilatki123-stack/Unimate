package com.example.unimate.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unimate.data.model.AttendanceLog
import com.example.unimate.data.model.AttendanceStatus
import com.example.unimate.data.model.SemesterEntryMode
import com.example.unimate.data.model.SemesterGpaRecord
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.UserProfile
import com.example.unimate.ui.attendance.AttendanceSectionContent
import com.example.unimate.ui.attendance.AttendanceUiState
import com.example.unimate.ui.components.UniMateLogoMark
import com.example.unimate.ui.courses.CourseDetailCard
import com.example.unimate.ui.courses.CoursesEmptyCard
import com.example.unimate.ui.courses.CoursesErrorCard
import com.example.unimate.ui.courses.CoursesLoadingCard
import com.example.unimate.ui.courses.CoursesModuleHeader
import com.example.unimate.ui.courses.CoursesUiState
import com.example.unimate.ui.gpa.GpaCgpaSectionContent
import com.example.unimate.ui.gpa.GpaCgpaUiState
import com.example.unimate.ui.notes.NotesQuickFilter
import com.example.unimate.ui.notes.NotesSectionContent
import com.example.unimate.ui.notes.NotesUiState

@Composable
fun AcademicsTabContent(
    profile: UserProfile,
    uiState: MainDashboardUiState,
    coursesState: CoursesUiState,
    courseActionMessage: String?,
    attendanceState: AttendanceUiState,
    attendanceActionMessage: String?,
    gpaState: GpaCgpaUiState,
    gpaActionMessage: String?,
    notesState: NotesUiState,
    notesActionMessage: String?,
    onSelectSubTab: (AcademicsSubTab) -> Unit,
    // Courses
    onAddCourseClick: () -> Unit,
    onEditCourseClick: (StudentCourse) -> Unit,
    onDeleteCourseClick: (String) -> Unit,
    onMarkCourseAttendance: (StudentCourse, Boolean) -> Unit,
    onRetryCourses: () -> Unit,
    // Attendance
    onClearAttendanceMessage: () -> Unit,
    onOpenLogAttendance: (StudentCourse?, AttendanceStatus) -> Unit,
    onOpenEditAttendanceTotals: (StudentCourse) -> Unit,
    onOpenThresholdDialog: () -> Unit,
    onSelectAttendanceCourseFilter: (String?) -> Unit,
    onSelectAttendanceMonthFilter: (String?) -> Unit,
    onToggleAttendanceLogStatus: (AttendanceLog) -> Unit,
    onDeleteAttendanceLog: (AttendanceLog) -> Unit,
    onRetryAttendance: () -> Unit,
    // GPA / CGPA
    onClearGpaMessage: () -> Unit,
    onAddSemesterGpaClick: (SemesterEntryMode) -> Unit,
    onEditSemesterGpaClick: (SemesterGpaRecord) -> Unit,
    onDeleteSemesterGpaClick: (String) -> Unit,
    onUpdateSemesterCourseGrade: (SemesterGpaRecord, String, String) -> Unit,
    onOpenGradingScaleDialog: () -> Unit,
    onRetryGpa: () -> Unit,
    // Notes & Profile
    onClearNotesMessage: () -> Unit,
    onSearchNotesChanged: (String) -> Unit,
    onSelectNotesSubjectFilter: (String?) -> Unit,
    onSelectNotesQuickFilter: (NotesQuickFilter) -> Unit,
    onCreateNoteClick: () -> Unit,
    onOpenNoteDetail: (StudyNote) -> Unit,
    onEditNoteClick: (StudyNote) -> Unit,
    onToggleNotePin: (StudyNote) -> Unit,
    onToggleNoteFavorite: (StudyNote) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetryNotes: () -> Unit,
    onOpenProfileSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("academics_tab_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UniMateLogoMark(size = 34.dp)
                        Column {
                            Text(
                                text = "UniMate Academics",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            )
                            Text(
                                text = "Courses • Attendance • GPA & CGPA • Notes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenProfileSheet,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("academics_manage_profile_button"),
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Profile")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AcademicsSubTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            AcademicsSubTab.COURSES -> Icons.AutoMirrored.Rounded.MenuBook
                            AcademicsSubTab.ATTENDANCE -> Icons.Rounded.EventAvailable
                            AcademicsSubTab.GPA_CGPA -> Icons.Rounded.Calculate
                            AcademicsSubTab.NOTES -> Icons.AutoMirrored.Rounded.Notes
                        }
                        FilterChip(
                            selected = uiState.academicsSubTab == tab,
                            onClick = { onSelectSubTab(tab) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text(tab.label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("academics_subtab_${tab.name.lowercase()}"),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (uiState.academicsSubTab) {
                AcademicsSubTab.COURSES -> {
                    CoursesSubTabList(
                        profile = profile,
                        coursesState = coursesState,
                        courseActionMessage = courseActionMessage,
                        onAddCourseClick = onAddCourseClick,
                        onEditCourseClick = onEditCourseClick,
                        onDeleteCourseClick = onDeleteCourseClick,
                        onMarkCourseAttendance = onMarkCourseAttendance,
                        onRetryCourses = onRetryCourses,
                        onOpenAttendanceTab = { onSelectSubTab(AcademicsSubTab.ATTENDANCE) },
                    )
                }

                AcademicsSubTab.ATTENDANCE -> {
                    AttendanceSectionContent(
                        uiState = attendanceState,
                        actionMessage = attendanceActionMessage,
                        onClearActionMessage = onClearAttendanceMessage,
                        onOpenLogAttendance = onOpenLogAttendance,
                        onOpenEditTotals = onOpenEditAttendanceTotals,
                        onOpenThresholdDialog = onOpenThresholdDialog,
                        onSelectCourseFilter = onSelectAttendanceCourseFilter,
                        onSelectMonthFilter = onSelectAttendanceMonthFilter,
                        onToggleLogStatus = onToggleAttendanceLogStatus,
                        onDeleteLog = onDeleteAttendanceLog,
                        onAddCourseShortcut = {
                            onSelectSubTab(AcademicsSubTab.COURSES)
                            onAddCourseClick()
                        },
                        onRetry = onRetryAttendance,
                    )
                }

                AcademicsSubTab.GPA_CGPA -> {
                    GpaCgpaSectionContent(
                        uiState = gpaState,
                        actionMessage = gpaActionMessage,
                        onClearActionMessage = onClearGpaMessage,
                        onAddSemesterClick = onAddSemesterGpaClick,
                        onEditSemesterClick = onEditSemesterGpaClick,
                        onDeleteSemesterClick = onDeleteSemesterGpaClick,
                        onUpdateCourseGrade = onUpdateSemesterCourseGrade,
                        onOpenGradingScaleDialog = onOpenGradingScaleDialog,
                        onRetry = onRetryGpa,
                    )
                }

                AcademicsSubTab.NOTES -> {
                    NotesSectionContent(
                        state = notesState,
                        actionMessage = notesActionMessage,
                        onClearActionMessage = onClearNotesMessage,
                        onSearchQueryChanged = onSearchNotesChanged,
                        onSelectSubjectFilter = onSelectNotesSubjectFilter,
                        onSelectQuickFilter = onSelectNotesQuickFilter,
                        onCreateNoteClick = onCreateNoteClick,
                        onOpenNoteDetail = onOpenNoteDetail,
                        onEditNoteClick = onEditNoteClick,
                        onTogglePin = onToggleNotePin,
                        onToggleFavorite = onToggleNoteFavorite,
                        onDeleteNote = onDeleteNote,
                        onRetry = onRetryNotes,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoursesSubTabList(
    profile: UserProfile,
    coursesState: CoursesUiState,
    courseActionMessage: String?,
    onAddCourseClick: () -> Unit,
    onEditCourseClick: (StudentCourse) -> Unit,
    onDeleteCourseClick: (String) -> Unit,
    onMarkCourseAttendance: (StudentCourse, Boolean) -> Unit,
    onRetryCourses: () -> Unit,
    onOpenAttendanceTab: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CoursesModuleHeader(
                coursesState = coursesState,
                actionMessage = courseActionMessage,
                onAddCourseClick = onAddCourseClick,
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }

        when (coursesState) {
            CoursesUiState.Loading -> {
                item { CoursesLoadingCard(modifier = Modifier.widthIn(max = 760.dp)) }
            }
            CoursesUiState.Empty -> {
                item {
                    CoursesEmptyCard(
                        onAddCourseClick = onAddCourseClick,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }
            is CoursesUiState.Error -> {
                item {
                    CoursesErrorCard(
                        message = coursesState.message,
                        onRetry = onRetryCourses,
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }
            is CoursesUiState.Success -> {
                items(coursesState.courses, key = { "course-detail-${it.id}" }) { course ->
                    CourseDetailCard(
                        course = course,
                        onEditClick = { onEditCourseClick(course) },
                        onDeleteClick = { onDeleteCourseClick(course.id) },
                        onMarkAttendance = { present -> onMarkCourseAttendance(course, present) },
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onOpenAttendanceTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full Attendance History & Shortage Alerts")
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = profile.fullName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "${profile.university} • ${profile.department} (${profile.semester})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
