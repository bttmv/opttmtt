package com.opengate.app.ui.screens.mylistings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Listing
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.ui.components.sanitizeErrorMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyListingsUiState(
    val loading: Boolean = true,
    val items: List<Listing> = emptyList(),
    val busyId: String? = null,
    val message: String? = null,
    val error: String? = null
)

class MyListingsViewModel(
    private val client: SupabaseClient,
    private val repo: ListingRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MyListingsUiState())
    val state: StateFlow<MyListingsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            _state.update { it.copy(loading = false, error = "سجّل الدخول لعرض إعلاناتك") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.getMyListings(userId) }
                .onSuccess { list -> _state.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = sanitizeErrorMessage(e)) } }
        }
    }

    fun markSold(l: Listing) = changeStatus(l, "sold", "تم تحديد الإعلان كمباع ✓")

    fun archive(l: Listing) = changeStatus(l, "archived", "تمت أرشفة الإعلان")

    private fun changeStatus(l: Listing, newStatus: String, okMessage: String) {
        val id = l.id ?: return
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repo.setMyListingStatus(id, newStatus) }
                .onSuccess {
                    _state.update { cur ->
                        cur.copy(
                            busyId = null,
                            message = okMessage,
                            items = cur.items.map { if (it.id == id) it.copy(status = newStatus) else it }
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busyId = null, error = sanitizeErrorMessage(e)) } }
        }
    }
}
