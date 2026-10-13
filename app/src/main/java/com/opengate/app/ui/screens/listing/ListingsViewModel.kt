package com.opengate.app.ui.screens.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Category
import com.opengate.app.data.model.Listing
import com.opengate.app.data.model.Platform
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.data.repository.PlatformRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** إعلان مع أول صوره المحمّلة — تُستخدم لبناء بطاقة الشبكة */
data class ListingCard(val listing: Listing, val imageUrls: List<String>)

data class ListingsUiState(
    val platform: Platform? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,   // null = "الكل"
    val cards: List<ListingCard> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

class ListingsViewModel(
    private val platformId: String,
    private val platformRepo: PlatformRepository,
    private val listingRepo: ListingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ListingsUiState())
    val state: StateFlow<ListingsUiState> = _state.asStateFlow()

    init { loadInitial() }

    private fun loadInitial() {
        viewModelScope.launch {
            runCatching { platformRepo.getPlatformById(platformId) }
                .onSuccess { _state.value = _state.value.copy(platform = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            runCatching { platformRepo.getCategories(platformId) }
                .onSuccess { _state.value = _state.value.copy(categories = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            loadListings()
        }
    }

    fun onCategorySelected(categoryId: String?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
        viewModelScope.launch { loadListings() }
    }

    private suspend fun loadListings() {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val listings = listingRepo.getPublishedListings(platformId, _state.value.selectedCategoryId)
            // نجلب صور كل إعلان بالتوازي بدل التسلسل لتسريع تحميل الشبكة
            coroutineScope {
                listings.map { l ->
                    async {
                        var urls = listingRepo.getListingImages(l.id ?: "").map { it.url }
                        if (urls.isEmpty()) {
                            val cover = (l.attributes["cover_image"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            val photos = (l.attributes["photos"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                            }
                            urls = if (!photos.isNullOrEmpty()) photos else if (cover != null) listOf(cover) else emptyList()
                        }
                        ListingCard(l, urls)
                    }
                }.awaitAll()
            }
        }.onSuccess { cards ->
            _state.value = _state.value.copy(cards = cards, loading = false)
        }.onFailure {
            _state.value = _state.value.copy(loading = false, error = it.message)
        }
    }
}
