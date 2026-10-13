package com.opengate.app.ui.screens.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.CategoryFieldSchema
import com.opengate.app.data.model.Listing
import com.opengate.app.data.model.RatingSummary
import com.opengate.app.data.model.Review
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.data.repository.PlatformRepository
import com.opengate.app.data.repository.ReviewRepository
import com.opengate.app.ui.components.sanitizeErrorMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ListingDetailUiState(
    val listing: Listing? = null,
    val imageUrls: List<String> = emptyList(),
    val fieldSchemas: List<CategoryFieldSchema> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val ratingSummary: RatingSummary = RatingSummary(),
    val loading: Boolean = true,
    val postingReview: Boolean = false,
    val error: String? = null
)

class ListingDetailViewModel(
    private val listingId: String,
    private val client: SupabaseClient,
    private val platformRepo: PlatformRepository,
    private val listingRepo: ListingRepository,
    private val reviewRepo: ReviewRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val listing = listingRepo.getListingById(listingId)
                var images = listingRepo.getListingImages(listingId).map { it.url }
                if (images.isEmpty()) {
                    val cover = (listing.attributes["cover_image"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val photos = (listing.attributes["photos"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                    images = if (!photos.isNullOrEmpty()) photos else if (cover != null) listOf(cover) else emptyList()
                }
                val fields = platformRepo.getFieldSchemas(listing.category_id)
                val reviews = reviewRepo.getListingReviews(listingId)
                val summary = reviewRepo.getListingRatingSummary(listingId)
                Quad(listing, images, fields, reviews, summary)
            }.onSuccess { (listing, images, fields, reviews, summary) ->
                _state.value = ListingDetailUiState(
                    listing = listing, imageUrls = images, fieldSchemas = fields,
                    reviews = reviews, ratingSummary = summary, loading = false
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = sanitizeErrorMessage(it))
            }
        }
    }

    /** ينشر تقييماً جديداً على هذا الإعلان تحديداً، ثم يحدّث القائمة والمتوسط فوراً */
    fun submitReview(rating: Int, comment: String) {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            _state.value = _state.value.copy(error = "سجّل دخولك أولاً لإضافة تقييم")
            return
        }

        val listing = _state.value.listing
        if (listing != null && listing.owner_id == userId) {
            _state.value = _state.value.copy(error = "لا يمكنك تقييم إعلانك الخاص")
            return
        }

        _state.value = _state.value.copy(postingReview = true, error = null)
        viewModelScope.launch {
            runCatching {
                reviewRepo.addReview(
                    Review(
                        reviewer_id = userId,
                        reviewee_id = listing?.owner_id,
                        listing_id = listingId,
                        rating = rating,
                        comment = comment.trim().ifBlank { null }
                    )
                )
            }.onSuccess {
                val reviews = reviewRepo.getListingReviews(listingId)
                val summary = reviewRepo.getListingRatingSummary(listingId)
                _state.value = _state.value.copy(postingReview = false, reviews = reviews, ratingSummary = summary, error = null)
            }.onFailure { ex ->
                val rawMsg = ex.message ?: ""
                val friendlyMsg = when {
                    rawMsg.contains("duplicate", ignoreCase = true) || rawMsg.contains("unique", ignoreCase = true) ->
                        "لقد قمت بتقييم هذا الإعلان مسبقاً"
                    rawMsg.contains("own", ignoreCase = true) || rawMsg.contains("نفس", ignoreCase = true) ->
                        "لا يمكنك تقييم إعلانك الخاص"
                    else ->
                        sanitizeErrorMessage(ex)
                }
                _state.value = _state.value.copy(postingReview = false, error = friendlyMsg)
            }
        }
    }

    /** يفتح محادثة مع بائع الإعلان (ينشئها أول مرة إن لم تكن موجودة)، ثم يمرر معرّفها للشاشة */
    fun startConversation(chatRepo: com.opengate.app.data.repository.ChatRepository, onReady: (String) -> Unit) {
        val listing = _state.value.listing ?: return
        val myId = client.auth.currentUserOrNull()?.id
        if (myId == null) {
            _state.value = _state.value.copy(error = "سجّل دخولك أولاً للتواصل مع البائع")
            return
        }
        if (myId == listing.owner_id) {
            _state.value = _state.value.copy(error = "هذا إعلانك أنت — لا يمكنك بدء محادثة معه")
            return
        }
        viewModelScope.launch {
            runCatching {
                chatRepo.getOrCreateConversation(listingId = listing.id, sellerId = listing.owner_id, buyerId = myId)
            }.onSuccess(onReady)
                .onFailure { _state.value = _state.value.copy(error = sanitizeErrorMessage(it)) }
        }
    }
}

// حاوية بسيطة لخمس قيم بدل تعريف data class منفصلة لنتيجة تحميل واحدة فقط
private data class Quad<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
