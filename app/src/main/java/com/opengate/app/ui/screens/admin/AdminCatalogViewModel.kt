package com.opengate.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Category
import com.opengate.app.data.model.CategoryFieldSchema
import com.opengate.app.data.model.Platform
import com.opengate.app.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminCatalogUiState(
    val platforms: List<Platform> = emptyList(),
    val selectedPlatform: Platform? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val fields: List<CategoryFieldSchema> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

/** إدارة التصنيفات والحقول الديناميكية — كل ما يُضاف هنا يظهر فوراً في نموذج إنشاء الإعلان */
class AdminCatalogViewModel(private val repo: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminCatalogUiState())
    val state: StateFlow<AdminCatalogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repo.getAllPlatformsForAdmin() }
                .onSuccess { _state.value = _state.value.copy(platforms = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun selectPlatform(p: Platform) {
        _state.value = _state.value.copy(selectedPlatform = p, selectedCategory = null, fields = emptyList(), categories = emptyList())
        loadCategories(p.id)
    }

    private fun loadCategories(platformId: String) {
        viewModelScope.launch {
            runCatching { repo.getCategoriesForAdmin(platformId) }
                .onSuccess { _state.value = _state.value.copy(categories = it) }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun selectCategory(c: Category) {
        _state.value = _state.value.copy(selectedCategory = c, fields = emptyList())
        loadFields(c.id)
    }

    private fun loadFields(categoryId: String) {
        viewModelScope.launch {
            runCatching { repo.getFieldsForAdmin(categoryId) }
                .onSuccess { _state.value = _state.value.copy(fields = it) }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun addCategory(name: String) {
        val p = _state.value.selectedPlatform ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.createCategory(p.id, "cat_" + System.currentTimeMillis(), name.trim()) }
                .onSuccess { loadCategories(p.id); _state.value = _state.value.copy(message = "تمت إضافة التصنيف", error = null) }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun toggleCategory(c: Category) {
        val p = _state.value.selectedPlatform ?: return
        viewModelScope.launch {
            runCatching { repo.setCategoryVisibility(c.id, !c.is_visible) }
                .onSuccess { loadCategories(p.id) }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun addField(label: String, type: String, required: Boolean, optionsCsv: String) {
        val c = _state.value.selectedCategory ?: return
        if (label.isBlank()) return
        val options = optionsCsv.split(",", "،", "/").map { it.trim() }.filter { it.isNotEmpty() }
        if ((type == "select" || type == "multi_select" || type == "choice_binary") && options.isEmpty()) {
            _state.value = _state.value.copy(error = "أدخل الخيارات مفصولة بشرطة / أو فاصلة")
            return
        }
        viewModelScope.launch {
            runCatching { repo.createField(c.id, "f_" + System.currentTimeMillis(), label.trim(), type, required, options) }
                .onSuccess { loadFields(c.id); _state.value = _state.value.copy(message = "تمت إضافة الحقل", error = null) }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun deleteField(f: CategoryFieldSchema) {
        val c = _state.value.selectedCategory ?: return
        viewModelScope.launch {
            runCatching { repo.deleteField(f.id) }
                .onSuccess { loadFields(c.id) }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }
}
