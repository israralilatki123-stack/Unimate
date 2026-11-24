package com.example.unimate.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unimate.data.model.AcademicSettings
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.assignments.AssignmentEditorDialog
import com.example.unimate.ui.assignments.AssignmentsViewModel
import com.example.unimate.ui.attendance.AttendanceViewModel
import com.example.unimate.ui.attendance.DirectTotalsEditorDialog
import com.example.unimate.ui.attendance.LogAttendanceDialog
import com.example.unimate.ui.attendance.ThresholdSettingsDialog
import com.example.unimate.ui.calendar.CalendarEventEditorDialog
import com.example.unimate.ui.calendar.CalendarViewModel
import com.example.unimate.ui.courses.CourseEditorDialog
import com.example.unimate.ui.courses.CoursesViewModel
import com.example.unimate.ui.exams.ExamEditorDialog
import com.example.unimate.ui.exams.ExamsViewModel
import com.example.unimate.ui.gpa.GpaCgpaUiState
import com.example.unimate.ui.gpa.GpaCgpaViewModel
import com.example.unimate.ui.gpa.GradingScaleEditorDialog
import com.example.unimate.ui.gpa.SemesterGpaEditorDialog
import com.example.unimate.ui.quizzes.QuizEditorDialog
import com.example.unimate.ui.quizzes.QuizzesViewModel
import com.example.unimate.ui.reminders.ReminderEditorDialog
import com.example.unimate.ui.reminders.RemindersViewModel
import com.example.unimate.ui.timetable.TimetableEditorDialog
import com.example.unimate.ui.timetable.TimetableViewModel

@Composable
fun ModuleDialogsHost(
    enrolledCourses: List<StudentCourse>,
    timetableViewModel: TimetableViewModel,
    coursesViewModel: CoursesViewModel,
    assignmentsViewModel: AssignmentsViewModel,
    quizzesViewModel: QuizzesViewModel,
    examsViewModel: ExamsViewModel,
    calendarViewModel: CalendarViewModel,
    remindersViewModel: RemindersViewModel,
    attendanceViewModel: AttendanceViewModel,
    gpaCgpaViewModel: GpaCgpaViewModel,
) {
    TimetableAndCourseDialogs(
        enrolledCourses = enrolledCourses,
        timetableViewModel = timetableViewModel,
        coursesViewModel = coursesViewModel,
    )
    AcademicWorkDialogs(
        enrolledCourses = enrolledCourses,
        assignmentsViewModel = assignmentsViewModel,
        quizzesViewModel = quizzesViewModel,
        examsViewModel = examsViewModel,
    )
    CalendarAndReminderDialogs(
        enrolledCourses = enrolledCourses,
        calendarViewModel = calendarViewModel,
        remindersViewModel = remindersViewModel,
    )
    AttendanceAndGpaDialogs(
        enrolledCourses = enrolledCourses,
        attendanceViewModel = attendanceViewModel,
        gpaCgpaViewModel = gpaCgpaViewModel,
    )
}

@Composable
private fun TimetableAndCourseDialogs(
    enrolledCourses: List<StudentCourse>,
    timetableViewModel: TimetableViewModel,
    coursesViewModel: CoursesViewModel,
) {
    val timetableEditorState by timetableViewModel.editorState.collectAsStateWithLifecycle()
    val courseEditorState by coursesViewModel.editorState.collectAsStateWithLifecycle()

    if (timetableEditorState.isOpen) {
        TimetableEditorDialog(
            state = timetableEditorState,
            enrolledCourses = enrolledCourses,
            onSelectCourseTemplate = timetableViewModel::applyCourseTemplate,
            onDayChanged = timetableViewModel::onDayChanged,
            onSubjectChanged = timetableViewModel::onSubjectNameChanged,
            onCourseCodeChanged = timetableViewModel::onCourseCodeChanged,
            onInstructorChanged = timetableViewModel::onInstructorChanged,
            onClassroomChanged = timetableViewModel::onClassroomChanged,
            onStartTimeChanged = timetableViewModel::onStartTimeChanged,
            onEndTimeChanged = timetableViewModel::onEndTimeChanged,
            onColorSelected = timetableViewModel::onColorSelected,
            onDismiss = timetableViewModel::dismissEditorDialog,
            onSave = timetableViewModel::saveClass,
        )
    }

    if (courseEditorState.isOpen) {
        CourseEditorDialog(
            state = courseEditorState,
            onCourseCodeChanged = coursesViewModel::onCourseCodeChanged,
            onCourseNameChanged = coursesViewModel::onCourseNameChanged,
            onCreditHoursChanged = coursesViewModel::onCreditHoursChanged,
            onInstructorChanged = coursesViewModel::onInstructorChanged,
            onSemesterChanged = coursesViewModel::onSemesterChanged,
            onDescriptionChanged = coursesViewModel::onDescriptionChanged,
            onClassroomChanged = coursesViewModel::onClassroomChanged,
            onScheduleChanged = coursesViewModel::onScheduleChanged,
            onAttendedChanged = coursesViewModel::onAttendedChanged,
            onTotalChanged = coursesViewModel::onTotalChanged,
            onColorSelected = coursesViewModel::onColorSelected,
            onDismiss = coursesViewModel::dismissEditorDialog,
            onSave = coursesViewModel::saveCourse,
        )
    }
}

@Composable
private fun AcademicWorkDialogs(
    enrolledCourses: List<StudentCourse>,
    assignmentsViewModel: AssignmentsViewModel,
    quizzesViewModel: QuizzesViewModel,
    examsViewModel: ExamsViewModel,
) {
    val assignmentEditorState by assignmentsViewModel.editorState.collectAsStateWithLifecycle()
    val quizEditorState by quizzesViewModel.editorState.collectAsStateWithLifecycle()
    val examEditorState by examsViewModel.editorState.collectAsStateWithLifecycle()

    if (assignmentEditorState.isOpen) {
        AssignmentEditorDialog(
            state = assignmentEditorState,
            enrolledCourses = enrolledCourses,
            onTitleChanged = assignmentsViewModel::onTitleChanged,
            onDescriptionChanged = assignmentsViewModel::onDescriptionChanged,
            onCourseChanged = assignmentsViewModel::onCourseChanged,
            onDeadlineChanged = assignmentsViewModel::onDeadlineChanged,
            onPriorityChanged = assignmentsViewModel::onPriorityChanged,
            onStatusChanged = assignmentsViewModel::onStatusChanged,
            onLinkDraftNameChanged = assignmentsViewModel::onLinkDraftNameChanged,
            onLinkDraftUriChanged = assignmentsViewModel::onLinkDraftUriChanged,
            onAddPickedAttachment = assignmentsViewModel::addPickedAttachment,
            onAddLinkAttachment = assignmentsViewModel::addLinkAttachmentFromDraft,
            onRemoveAttachment = assignmentsViewModel::removeAttachment,
            onDismiss = assignmentsViewModel::dismissDialog,
            onSave = assignmentsViewModel::saveAssignment,
        )
    }

    if (quizEditorState.isOpen) {
        QuizEditorDialog(
            state = quizEditorState,
            enrolledCourses = enrolledCourses,
            onTitleChanged = quizzesViewModel::onTitleChanged,
            onCourseChanged = quizzesViewModel::onCourseChanged,
            onScheduledAtChanged = quizzesViewModel::onScheduledAtChanged,
            onTotalMarksChanged = quizzesViewModel::onTotalMarksChanged,
            onScoreRecordedChanged = quizzesViewModel::onScoreRecordedChanged,
            onObtainedMarksChanged = quizzesViewModel::onObtainedMarksChanged,
            onPreparationStatusChanged = quizzesViewModel::onPreparationStatusChanged,
            onNotesChanged = quizzesViewModel::onNotesChanged,
            onDismiss = quizzesViewModel::dismissDialog,
            onSave = quizzesViewModel::saveQuiz,
        )
    }

    if (examEditorState.isOpen) {
        ExamEditorDialog(
            state = examEditorState,
            enrolledCourses = enrolledCourses,
            onTitleChanged = examsViewModel::onTitleChanged,
            onExamTypeChanged = examsViewModel::onExamTypeChanged,
            onCourseChanged = examsViewModel::onCourseChanged,
            onExamAtChanged = examsViewModel::onExamAtChanged,
            onRoomChanged = examsViewModel::onRoomChanged,
            onPreparationStatusChanged = examsViewModel::onPreparationStatusChanged,
            onChecklistTopicDraftChanged = examsViewModel::onChecklistTopicDraftChanged,
            onAddChecklistTopic = examsViewModel::addChecklistTopicInEditor,
            onToggleChecklistTopic = examsViewModel::toggleChecklistTopicInEditor,
            onRemoveChecklistTopic = examsViewModel::removeChecklistTopicInEditor,
            onDismiss = examsViewModel::dismissDialog,
            onSave = examsViewModel::saveExam,
        )
    }
}

@Composable
private fun CalendarAndReminderDialogs(
    enrolledCourses: List<StudentCourse>,
    calendarViewModel: CalendarViewModel,
    remindersViewModel: RemindersViewModel,
) {
    val calendarEditorState by calendarViewModel.editorState.collectAsStateWithLifecycle()
    val reminderEditorState by remindersViewModel.editorState.collectAsStateWithLifecycle()

    if (calendarEditorState.isOpen) {
        CalendarEventEditorDialog(
            state = calendarEditorState,
            enrolledCourses = enrolledCourses,
            onTitleChanged = calendarViewModel::onTitleChanged,
            onDescriptionChanged = calendarViewModel::onDescriptionChanged,
            onCategoryChanged = calendarViewModel::onCategoryChanged,
            onLocationOrCourseChanged = calendarViewModel::onLocationOrCourseChanged,
            onAllDayChanged = calendarViewModel::onAllDayChanged,
            onStartAtChanged = calendarViewModel::onStartAtChanged,
            onEndAtChanged = calendarViewModel::onEndAtChanged,
            onDismiss = calendarViewModel::dismissEditorDialog,
            onSave = calendarViewModel::saveEvent,
        )
    }

    if (reminderEditorState.isOpen) {
        ReminderEditorDialog(
            state = reminderEditorState,
            onTitleChanged = remindersViewModel::onTitleChanged,
            onNotesChanged = remindersViewModel::onNotesChanged,
            onCategoryChanged = remindersViewModel::onCategoryChanged,
            onTriggerAtChanged = remindersViewModel::onTriggerAtChanged,
            onLeadMinutesChanged = remindersViewModel::onLeadMinutesChanged,
            onEnabledChanged = remindersViewModel::onEnabledChanged,
            onDismiss = remindersViewModel::dismissDialog,
            onSave = remindersViewModel::saveReminder,
        )
    }
}

@Composable
private fun AttendanceAndGpaDialogs(
    enrolledCourses: List<StudentCourse>,
    attendanceViewModel: AttendanceViewModel,
    gpaCgpaViewModel: GpaCgpaViewModel,
) {
    val attendanceLogEditorState by attendanceViewModel.logEditorState.collectAsStateWithLifecycle()
    val attendanceTotalsEditorState by attendanceViewModel.totalsEditorState.collectAsStateWithLifecycle()
    val thresholdEditorState by attendanceViewModel.thresholdEditorState.collectAsStateWithLifecycle()
    val gpaState by gpaCgpaViewModel.uiState.collectAsStateWithLifecycle()
    val semesterEditorState by gpaCgpaViewModel.semesterEditorState.collectAsStateWithLifecycle()
    val scaleEditorState by gpaCgpaViewModel.scaleEditorState.collectAsStateWithLifecycle()

    if (attendanceLogEditorState.isOpen) {
        LogAttendanceDialog(
            state = attendanceLogEditorState,
            courses = enrolledCourses,
            onCourseSelected = attendanceViewModel::onLogCourseSelected,
            onStatusSelected = attendanceViewModel::onLogStatusSelected,
            onDateSelected = attendanceViewModel::onLogDateSelected,
            onNotesChanged = attendanceViewModel::onLogNotesChanged,
            onDismiss = attendanceViewModel::dismissLogAttendanceDialog,
            onSave = attendanceViewModel::saveManualAttendanceLog,
        )
    }

    if (attendanceTotalsEditorState.isOpen) {
        DirectTotalsEditorDialog(
            state = attendanceTotalsEditorState,
            onAttendedChanged = attendanceViewModel::onDirectAttendedChanged,
            onTotalChanged = attendanceViewModel::onDirectTotalChanged,
            onDismiss = attendanceViewModel::dismissDirectTotalsDialog,
            onSave = attendanceViewModel::saveDirectCourseTotals,
        )
    }

    if (thresholdEditorState.isOpen) {
        ThresholdSettingsDialog(
            state = thresholdEditorState,
            onThresholdChanged = attendanceViewModel::onThresholdChanged,
            onDismiss = attendanceViewModel::dismissThresholdDialog,
            onSave = attendanceViewModel::saveThresholdSettings,
        )
    }

    val activeScale = when (val gState = gpaState) {
        is GpaCgpaUiState.Success -> gState.settings.gradingScale
        is GpaCgpaUiState.Empty -> gState.settings.gradingScale
        else -> AcademicSettings().gradingScale
    }

    if (semesterEditorState.isOpen) {
        SemesterGpaEditorDialog(
            state = semesterEditorState,
            enrolledCourses = enrolledCourses,
            gradingScale = activeScale,
            onSemesterNameChanged = gpaCgpaViewModel::onSemesterNameChanged,
            onOrderChanged = gpaCgpaViewModel::onSemesterOrderChanged,
            onActiveSemesterChanged = gpaCgpaViewModel::onActiveSemesterChanged,
            onEntryModeChanged = gpaCgpaViewModel::onSemesterEntryModeChanged,
            onSummaryCreditsChanged = gpaCgpaViewModel::onSummaryCreditsChanged,
            onSummaryGpaChanged = gpaCgpaViewModel::onSummaryGpaChanged,
            onImportAllEnrolled = gpaCgpaViewModel::importAllEnrolledCoursesToDraft,
            onAddEnrolledCourse = gpaCgpaViewModel::addEnrolledCourseToDraft,
            onDraftCourseCodeChanged = gpaCgpaViewModel::onDraftCourseCodeChanged,
            onDraftCourseNameChanged = gpaCgpaViewModel::onDraftCourseNameChanged,
            onDraftCreditHoursChanged = gpaCgpaViewModel::onDraftCreditHoursChanged,
            onDraftLetterGradeChanged = gpaCgpaViewModel::onDraftLetterGradeChanged,
            onAddCustomCourse = gpaCgpaViewModel::addDraftCustomCourse,
            onUpdateDraftCourseGrade = gpaCgpaViewModel::updateDraftCourseGrade,
            onRemoveDraftCourse = gpaCgpaViewModel::removeDraftCourse,
            onDismiss = gpaCgpaViewModel::dismissSemesterDialog,
            onSave = gpaCgpaViewModel::saveSemesterRecord,
        )
    }

    if (scaleEditorState.isOpen) {
        GradingScaleEditorDialog(
            state = scaleEditorState,
            onScaleNameChanged = gpaCgpaViewModel::onScaleNameChanged,
            onRowLetterChanged = gpaCgpaViewModel::onScaleRowLetterChanged,
            onRowPointsChanged = gpaCgpaViewModel::onScaleRowPointsChanged,
            onAddRow = gpaCgpaViewModel::addScaleRow,
            onRemoveRow = gpaCgpaViewModel::removeScaleRow,
            onApplyPreset = gpaCgpaViewModel::applyScalePreset,
            onDismiss = gpaCgpaViewModel::dismissGradingScaleDialog,
            onSave = gpaCgpaViewModel::saveGradingScaleSettings,
        )
    }
}
