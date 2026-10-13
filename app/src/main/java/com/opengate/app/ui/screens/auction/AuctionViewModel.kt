package com.opengate.app.ui.screens.auction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Auction
import com.opengate.app.data.model.Bid
import com.opengate.app.data.repository.AuctionRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

data class AuctionUiState(
    val auction: Auction? = null,
    val bids: List<Bid> = emptyList(),
    val myUserId: String? = null,
    val bidInput: String = "",
    val remainingSeconds: Long = 0,
    val loading: Boolean = true,
    val placingBid: Boolean = false,
    val error: String? = null
) {
    val minNextBid: Double
        get() {
            val a = auction ?: return 0.0
            return (a.current_price ?: a.starting_price) + a.min_increment
        }
}

class AuctionViewModel(
    private val auctionId: String,
    private val client: SupabaseClient,
    private val repo: AuctionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuctionUiState())
    val state: StateFlow<AuctionUiState> = _state.asStateFlow()

    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: RealtimeChannel? = null

    init {
        _state.value = _state.value.copy(myUserId = client.auth.currentUserOrNull()?.id)
        load()
        startCountdown()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                val auction = repo.getAuctionById(auctionId)
                val bids = repo.getBids(auctionId)
                auction to bids
            }.onSuccess { (auction, bids) ->
                _state.value = _state.value.copy(auction = auction, bids = bids, loading = false)
                listenLive()
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = com.opengate.app.ui.components.sanitizeErrorMessage(it))
            }
        }
    }

    private fun listenLive() {
        channel = repo.subscribeToAuction(
            auctionId = auctionId,
            scope = realtimeScope,
            onPriceUpdate = { updated -> _state.value = _state.value.copy(auction = updated) },
            onNewBid = { bid -> _state.value = _state.value.copy(bids = listOf(bid) + _state.value.bids) }
        )
    }

    /** عدّاد تنازلي بسيط لوقت انتهاء المزاد، يعتمد على ends_at القادم من القاعدة */
    private fun startCountdown() {
        viewModelScope.launch {
            while (true) {
                val endsAt = _state.value.auction?.ends_at
                if (endsAt != null) {
                    val remaining = runCatching {
                        OffsetDateTime.parse(endsAt).toInstant().epochSecond - System.currentTimeMillis() / 1000
                    }.getOrDefault(0L)
                    _state.value = _state.value.copy(remainingSeconds = remaining.coerceAtLeast(0))
                }
                delay(1000)
            }
        }
    }

    fun onBidInputChange(v: String) { _state.value = _state.value.copy(bidInput = v, error = null) }

    fun placeBid() {
        if (_state.value.placingBid) return
        // يقبل الأرقام العربية الهندية والفواصل
        val amount = com.opengate.app.util.PhoneUtils.toAsciiDigits(_state.value.bidInput)
            .replace(",", "").replace("٫", ".").trim().toDoubleOrNull()
        val userId = _state.value.myUserId
        if (amount == null || amount <= 0 || amount >= 1_000_000_000_000.0) { _state.value = _state.value.copy(error = "أدخل مبلغاً صحيحاً"); return }
        if (userId == null) { _state.value = _state.value.copy(error = "سجّل دخولك أولاً للمزايدة"); return }
        if (amount < _state.value.minNextBid) {
            _state.value = _state.value.copy(error = "أقل مزايدة مقبولة الآن: ${_state.value.minNextBid}")
            return
        }
        _state.value = _state.value.copy(placingBid = true)
        viewModelScope.launch {
            runCatching { repo.placeBid(auctionId, userId, amount) }
                .onSuccess { _state.value = _state.value.copy(placingBid = false, bidInput = "") }
                .onFailure {
                    // رسائل رفض المزايدة عربية وتأتي من trigger القاعدة (guard_bid_insert) وتمرّ عبر المنقّي المركزي
                    _state.value = _state.value.copy(
                        placingBid = false,
                        error = com.opengate.app.ui.components.sanitizeErrorMessage(it)
                    )
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
