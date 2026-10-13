package com.opengate.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.FeaturedSponsor
import com.opengate.app.data.model.Platform
import com.opengate.app.data.model.SocialLink
import com.opengate.app.data.repository.AdminRepository
import com.opengate.app.data.repository.ContentRepository
import com.opengate.app.data.repository.PlatformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val platforms: List<Platform> = emptyList(),
    val sponsors: List<FeaturedSponsor> = emptyList(),
    val socialLinks: List<SocialLink> = emptyList(),
    val isAdmin: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val repo: PlatformRepository,
    private val adminRepo: AdminRepository,
    private val contentRepo: ContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // الاستماع الفوري لتغييرات المنصات الظاهرة والمخفية لحظياً
        viewModelScope.launch {
            com.opengate.app.data.platforms.PlatformsStore.visiblePlatforms.collect { visibleList ->
                _state.value = _state.value.copy(platforms = visibleList, loading = false)
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            com.opengate.app.data.platforms.PlatformsStore.refresh()
            runCatching { repo.getVisiblePlatforms() }
                .onSuccess { platforms ->
                    _state.value = _state.value.copy(loading = false, platforms = platforms)
                }

            // الشريط المتحرك وحسابات التواصل
            runCatching { contentRepo.getVisibleSponsors() }
                .onSuccess { _state.value = _state.value.copy(sponsors = it) }
            runCatching { contentRepo.getVisibleSocialLinks() }
                .onSuccess { _state.value = _state.value.copy(socialLinks = it) }

            // فحص صلاحية الإدارة
            runCatching { adminRepo.getMyAdminStatus() }
                .onSuccess { status -> _state.value = _state.value.copy(isAdmin = status != null) }
                .onFailure { /* المستخدم عادي */ }
        }
    }
}
