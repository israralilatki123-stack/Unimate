package com.example.unimate.ui.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.sync.NetworkMonitor
import com.example.unimate.data.sync.NetworkState
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

interface AiStudyEngine {
    fun generateStream(prompt: String): Flow<String>
}

class FirebaseAiStudyEngine(
    modelName: String = "gemini-3.8-flash",
) : AiStudyEngine {
    private val model by lazy {
        Firebase.ai.generativeModel(modelName)
    }

    override fun generateStream(prompt: String): Flow<String> =
        model.generateContentStream(prompt).map { chunk -> chunk.text.orEmpty() }
}

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
)

data class AiAssistantUiState(
    val inputText: String = "",
    val messages: List<AiChatMessage> = emptyList(),
    val inferenceState: AiUiState = AiUiState.Idle,
    val isOnline: Boolean = true,
) {
    val isGenerating: Boolean
        get() = inferenceState is AiUiState.Loading || inferenceState is AiUiState.Streaming
}

class AiAssistantViewModel(
    private val studyEngine: AiStudyEngine = FirebaseAiStudyEngine(),
    private val networkMonitor: NetworkMonitor? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiAssistantUiState(isOnline = networkMonitor?.isOnline ?: true),
    )
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    init {
        if (networkMonitor != null) {
            viewModelScope.launch {
                networkMonitor.networkState.collectLatest { state ->
                    val online = state == NetworkState.ONLINE
                    _uiState.update { current ->
                        current.copy(isOnline = online)
                    }
                }
            }
        }
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun sendPrompt(
        promptOverride: String? = null,
        studentContextSummary: String = "",
    ) {
        val rawPrompt = (promptOverride ?: _uiState.value.inputText).trim()
        if (rawPrompt.isEmpty() || _uiState.value.isGenerating) return

        if (networkMonitor?.isOnline == false) {
            _uiState.update { current ->
                current.copy(
                    isOnline = false,
                    inputText = if (promptOverride != null && current.inputText.isBlank()) {
                        rawPrompt
                    } else {
                        current.inputText
                    },
                    inferenceState = AiUiState.Error(
                        "You are offline. UniMate AI requires an internet connection — your draft has been preserved.",
                    ),
                )
            }
            return
        }

        val userMessage = AiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = rawPrompt,
        )
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = AiChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            isStreaming = true,
        )

        _uiState.update {
            it.copy(
                inputText = if (promptOverride == null) "" else it.inputText,
                messages = it.messages + userMessage + assistantPlaceholder,
                inferenceState = AiUiState.Loading,
            )
        }

        val fullPrompt = buildString {
            appendLine("You are UniMate AI Study Assistant helping a university student.")
            if (studentContextSummary.isNotBlank()) {
                appendLine("Student Academic Context: $studentContextSummary")
            }
            appendLine("Provide clear, structured, actionable academic help.")
            appendLine("Student Question: $rawPrompt")
        }

        viewModelScope.launch {
            val accumulatedText = StringBuilder()
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    studyEngine.generateStream(fullPrompt).collect { chunkText ->
                        accumulatedText.append(chunkText)
                        val partial = accumulatedText.toString()
                        _uiState.update { state ->
                            state.copy(
                                inferenceState = AiUiState.Streaming(partial),
                                messages = state.messages.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(text = partial, isStreaming = true)
                                    } else {
                                        msg
                                    }
                                },
                            )
                        }
                    }
                }
                val finalText = accumulatedText.toString().trim()
                if (finalText.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            inferenceState = AiUiState.Success(finalText),
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(text = finalText, isStreaming = false)
                                } else {
                                    msg
                                }
                            },
                        )
                    }
                } else {
                    val emptyError = "Gemini returned an empty response. Please try again."
                    _uiState.update { state ->
                        state.copy(
                            inferenceState = AiUiState.Error(emptyError),
                            messages = state.messages.filterNot { it.id == assistantMsgId },
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val timeoutMsg = "AI response timed out after 60 seconds. Please try again."
                _uiState.update { state ->
                    state.copy(
                        inferenceState = AiUiState.Error(timeoutMsg),
                        messages = state.messages.filterNot { it.id == assistantMsgId && it.text.isBlank() }
                            .map { if (it.id == assistantMsgId) it.copy(isStreaming = false) else it },
                    )
                }
                runCatching { Log.e(TAG, "AI generation timed out", e) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorText = e.localizedMessage ?: "Failed to generate AI response."
                _uiState.update { state ->
                    state.copy(
                        inferenceState = AiUiState.Error(errorText),
                        messages = state.messages.filterNot { it.id == assistantMsgId && it.text.isBlank() }
                            .map { if (it.id == assistantMsgId) it.copy(isStreaming = false) else it },
                    )
                }
                runCatching { Log.e(TAG, "Firebase AI generation failed", e) }
            }
        }
    }

    fun dismissError() {
        if (_uiState.value.inferenceState is AiUiState.Error) {
            _uiState.update { it.copy(inferenceState = AiUiState.Idle) }
        }
    }

    fun clearConversation() {
        if (_uiState.value.isGenerating) return
        _uiState.value = AiAssistantUiState(isOnline = networkMonitor?.isOnline ?: true)
    }

    companion object {
        private const val TAG = "AiAssistantVM"
        private const val AI_TIMEOUT_MS = 60_000L

        fun factory(
            studyEngine: AiStudyEngine = FirebaseAiStudyEngine(),
            networkMonitor: NetworkMonitor? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiAssistantViewModel(
                    studyEngine = studyEngine,
                    networkMonitor = networkMonitor,
                ) as T
            }
        }
    }
}
