package com.example.unimate.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unimate.data.model.StudentCourse
import com.example.unimate.ui.notes.NoteDetailDialog
import com.example.unimate.ui.notes.NoteEditorDialog
import com.example.unimate.ui.notes.NotesViewModel
import com.example.unimate.ui.studyplanner.PlannerGoalsSettingsDialog
import com.example.unimate.ui.studyplanner.StudyPlannerViewModel
import com.example.unimate.ui.studyplanner.StudySessionEditorDialog

@Composable
fun NotesAndStudyPlannerDialogsHost(
    notesViewModel: NotesViewModel,
    studyPlannerViewModel: StudyPlannerViewModel,
    enrolledCourses: List<StudentCourse>,
) {
    val noteEditorState by notesViewModel.editorState.collectAsStateWithLifecycle()
    val viewingNote by notesViewModel.viewingNote.collectAsStateWithLifecycle()
    val sessionEditorState by studyPlannerViewModel.sessionEditorState.collectAsStateWithLifecycle()
    val goalsEditorState by studyPlannerViewModel.goalsEditorState.collectAsStateWithLifecycle()

    if (noteEditorState.isOpen) {
        NoteEditorDialog(
            state = noteEditorState,
            enrolledCourses = enrolledCourses,
            onTitleChanged = notesViewModel::onTitleChanged,
            onSubjectChanged = notesViewModel::onSubjectChanged,
            onContentChanged = notesViewModel::onContentChanged,
            onPinnedChanged = notesViewModel::onEditorPinnedChanged,
            onFavoriteChanged = notesViewModel::onEditorFavoriteChanged,
            onLinkDraftUriChanged = notesViewModel::onLinkDraftUriChanged,
            onLinkDraftNameChanged = notesViewModel::onLinkDraftNameChanged,
            onAddPickedAttachment = notesViewModel::addPickedAttachment,
            onAddLinkAttachment = notesViewModel::addLinkAttachmentFromDraft,
            onRemoveAttachment = notesViewModel::removeAttachment,
            onDismiss = notesViewModel::dismissEditorDialog,
            onSave = notesViewModel::saveNote,
        )
    }

    val detailNote = viewingNote
    if (detailNote != null) {
        NoteDetailDialog(
            note = detailNote,
            onTogglePin = { notesViewModel.togglePinned(detailNote) },
            onToggleFavorite = { notesViewModel.toggleFavorite(detailNote) },
            onEdit = { notesViewModel.openEditDialog(detailNote) },
            onDelete = { notesViewModel.deleteNote(detailNote.id) },
            onDismiss = notesViewModel::dismissNoteDetail,
        )
    }

    if (sessionEditorState.isOpen) {
        StudySessionEditorDialog(
            state = sessionEditorState,
            enrolledCourses = enrolledCourses,
            onTitleChanged = studyPlannerViewModel::onSessionTitleChanged,
            onSubjectChanged = studyPlannerViewModel::onSessionSubjectChanged,
            onDurationChanged = studyPlannerViewModel::onSessionDurationChanged,
            onCompletedChanged = studyPlannerViewModel::onSessionCompletedChanged,
            onScheduledAtChanged = studyPlannerViewModel::onSessionScheduledAtChanged,
            onNotesChanged = studyPlannerViewModel::onSessionNotesChanged,
            onDismiss = studyPlannerViewModel::dismissSessionDialog,
            onSave = studyPlannerViewModel::saveStudySession,
        )
    }

    if (goalsEditorState.isOpen) {
        PlannerGoalsSettingsDialog(
            state = goalsEditorState,
            onDailyChanged = studyPlannerViewModel::onDailyGoalMinutesChanged,
            onWeeklyChanged = studyPlannerViewModel::onWeeklyGoalMinutesChanged,
            onFocusMinutesChanged = studyPlannerViewModel::onFocusMinutesSettingChanged,
            onBreakMinutesChanged = studyPlannerViewModel::onBreakMinutesSettingChanged,
            onDismiss = studyPlannerViewModel::dismissGoalsDialog,
            onSave = studyPlannerViewModel::saveGoalSettings,
        )
    }
}
