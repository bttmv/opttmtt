package com.opengate.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.ConversationRow
import com.opengate.app.data.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatListUiState(
    val myUserId: String? = null,
    val conversations: List<ConversationRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

class ChatListViewModel(private val client: SupabaseClient, private val repo: ChatRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init {
        val userId = client.auth.currentUserOrNull()?.id
        _state.value = _state.value.copy(myUserId = userId)
        if (userId != null) load(userId)
        else _state.value = _state.value.copy(loading = false, error = "سجّل دخولك لعرض محادثاتك")
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            runCatching { repo.getMyConversations(userId) }
                .onSuccess { _state.value = _state.value.copy(conversations = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}
