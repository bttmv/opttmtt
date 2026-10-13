package com.opengate.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.FeaturedSponsor
import com.opengate.app.data.model.SocialLink
import com.opengate.app.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminContentUiState(
    val socialLinks: List<SocialLink> = emptyList(),
    val sponsors: List<FeaturedSponsor> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

class AdminContentViewModel(private val repo: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminContentUiState())
    val state: StateFlow<AdminContentUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { repo.getSocialLinksForAdmin() to repo.getSponsorsForAdmin() }
                .onSuccess { (links, sponsors) -> _state.value = AdminContentUiState(links, sponsors, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    /** يحفظ الرابط وحالة الإظهار معاً. لن يظهر الحساب للجمهور إلا إذا كانت القيمة غير فارغة والإظهار مفعّلاً */
    fun saveSocial(key: String, value: String, visible: Boolean) {
        viewModelScope.launch {
            runCatching { repo.updateSocialLink(key, value, visible && value.isNotBlank()) }
                .onSuccess { load(); _state.value = _state.value.copy(message = "تم الحفظ") }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun addSponsor(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.addSponsor(text) }
                .onSuccess { load() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun toggleSponsor(s: FeaturedSponsor) {
        viewModelScope.launch {
            runCatching { repo.setSponsorVisible(s.id, !s.is_visible) }
                .onSuccess { load() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deleteSponsor(s: FeaturedSponsor) {
        viewModelScope.launch {
            runCatching { repo.deleteSponsor(s.id) }
                .onSuccess { load() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
}
