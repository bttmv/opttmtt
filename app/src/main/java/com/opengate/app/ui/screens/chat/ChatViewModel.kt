package com.opengate.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Message
import com.opengate.app.data.repository.ChatRepository
import com.opengate.app.ui.components.sanitizeErrorMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val myUserId: String? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val loading: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val conversationId: String,
    private val client: SupabaseClient,
    private val repo: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // Scope منفصل عن viewModelScope لضمان إمكانية إغلاق قناة Realtime بأدب قبل الإلغاء الكامل
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: RealtimeChannel? = null

    init {
        _state.value = _state.value.copy(myUserId = client.auth.currentUserOrNull()?.id)
        loadMessages()
        listenForNewMessages()
        startFallbackSync()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            runCatching { repo.getMessages(conversationId) }
                .onSuccess { latest -> _state.update { it.copy(messages = latest, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = sanitizeErrorMessage(e)) } }
        }
    }

    /** مزامنة احتياطية بطيئة فقط (الوصول اللحظي يتم عبر Realtime) */
    private fun startFallbackSync() {
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(20_000)
                runCatching { repo.getMessages(conversationId) }.onSuccess { latest ->
                    _state.update { cur ->
                        val pendingOptimistic = cur.messages.filter { it.id?.startsWith("opt-") == true }
                        if (latest.size != cur.messages.size - pendingOptimistic.size ||
                            latest.lastOrNull()?.id != cur.messages.lastOrNull { it.id?.startsWith("opt-") != true }?.id
                        ) cur.copy(messages = latest + pendingOptimistic) else cur
                    }
                }
            }
        }
    }

    private fun listenForNewMessages() {
        channel = repo.subscribeToMessages(conversationId, realtimeScope) { incoming ->
            _state.update { cur ->
                if (cur.messages.any { it.id != null && it.id == incoming.id }) return@update cur
                // استبدال النسخة التفاؤلية (رسالتي) بالنسخة الحقيقية القادمة من الخادم
                val optimisticIdx = cur.messages.indexOfFirst {
                    it.id?.startsWith("opt-") == true &&
                        it.sender_id == incoming.sender_id && it.content == incoming.content
                }
                if (optimisticIdx >= 0) {
                    cur.copy(messages = cur.messages.toMutableList().also { it[optimisticIdx] = incoming })
                } else {
                    cur.copy(messages = cur.messages + incoming)
                }
            }
        }
    }

    fun onDraftChange(v: String) { _state.update { it.copy(draft = v) } }

    fun clearError() { _state.update { it.copy(error = null) } }

    fun send() {
        val text = _state.value.draft.trim()
        val userId = _state.value.myUserId
        if (text.isBlank() || userId == null || _state.value.sending) return
        if (text.length > 4000) {
            _state.update { it.copy(error = "الرسالة طويلة جداً (الحد الأقصى 4000 حرف)") }
            return
        }

        // تحديث تفاؤلي فوري ليظهر الكلام عند الضغط
        val optimisticId = "opt-${System.currentTimeMillis()}"
        val optimisticMsg = Message(
            id = optimisticId,
            conversation_id = conversationId,
            sender_id = userId,
            content = text,
            created_at = "الآن"
        )
        _state.update { it.copy(draft = "", messages = it.messages + optimisticMsg, sending = true) }

        viewModelScope.launch {
            runCatching { repo.sendMessage(conversationId, userId, text) }
                .onSuccess { _state.update { it.copy(sending = false) } }
                .onFailure { e ->
                    // إزالة الرسالة التفاؤلية وإرجاع النص للمسودة حتى لا يضيع
                    _state.update {
                        it.copy(
                            sending = false,
                            draft = if (it.draft.isBlank()) text else it.draft,
                            messages = it.messages.filterNot { m -> m.id == optimisticId },
                            error = sanitizeErrorMessage(e)
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        val ch = channel
        realtimeScope.launch {
            if (ch != null) repo.leaveChannel(ch)
            realtimeScope.cancel()
        }
        super.onCleared()
    }
}
