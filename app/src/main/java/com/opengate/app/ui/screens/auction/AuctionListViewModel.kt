package com.opengate.app.ui.screens.auction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.AuctionRow
import com.opengate.app.data.repository.AuctionRepository
import com.opengate.app.ui.components.sanitizeErrorMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuctionListUiState(
    val auctions: List<AuctionRow> = emptyList(),
    val loading: Boolean = true,
    val creating: Boolean = false,
    val error: String? = null
)

class AuctionListViewModel(
    private val repo: AuctionRepository,
    private val client: SupabaseClient
) : ViewModel() {

    private val _state = MutableStateFlow(AuctionListUiState())
    val state: StateFlow<AuctionListUiState> = _state.asStateFlow()

    init {
        loadAuctions()
    }

    fun loadAuctions() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repo.getLiveAuctions() }
                .onSuccess { _state.value = _state.value.copy(auctions = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = sanitizeErrorMessage(it)) }
        }
    }

    fun startAuction(
        title: String,
        startingPrice: Double,
        minIncrement: Double,
        durationHours: Long,
        city: String?,
        contactPhone: String?,
        onSuccess: (String) -> Unit
    ) {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            _state.value = _state.value.copy(error = "يجب تسجيل الدخول أولاً لتتمكن من بدء مزاد")
            return
        }

        _state.value = _state.value.copy(creating = true, error = null)
        viewModelScope.launch {
            runCatching {
                repo.startNewAuction(
                    title = title,
                    startingPrice = startingPrice,
                    minIncrement = minIncrement,
                    durationHours = durationHours,
                    city = city,
                    contactPhone = contactPhone
                )
            }.onSuccess { auctionId ->
                _state.value = _state.value.copy(creating = false)
                loadAuctions()
                onSuccess(auctionId)
            }.onFailure { ex ->
                _state.value = _state.value.copy(creating = false, error = sanitizeErrorMessage(ex))
            }
        }
    }
}
