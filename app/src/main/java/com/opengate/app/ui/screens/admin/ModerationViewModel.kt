package com.opengate.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Listing
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.ui.components.sanitizeErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationUiState(
    val loading: Boolean = true,
    val items: List<Listing> = emptyList(),
    val busyId: String? = null,
    val message: String? = null,
    val error: String? = null
)

/** طابور مراجعة الإعلانات المعلّقة (لمن يملك صلاحية listings.approve؛ تُفرض في القاعدة). */
class ModerationViewModel(private val repo: ListingRepository) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState())
    val state: StateFlow<ModerationUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.getPendingListings() }
                .onSuccess { list -> _state.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = sanitizeErrorMessage(e)) } }
        }
    }

    fun approve(listing: Listing) = moderate(listing, true, null)

    fun reject(listing: Listing, reason: String) = moderate(listing, false, reason)

    private fun moderate(listing: Listing, approve: Boolean, reason: String?) {
        val id = listing.id ?: return
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repo.moderateListing(id, approve, reason) }
                .onSuccess {
                    _state.update { cur ->
                        cur.copy(
                            busyId = null,
                            items = cur.items.filterNot { it.id == id },
                            message = if (approve) "تم اعتماد الإعلان ونشره ✓" else "تم رفض الإعلان"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busyId = null, error = sanitizeErrorMessage(e)) } }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null, error = null) } }
}
