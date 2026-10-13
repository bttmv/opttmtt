package com.opengate.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.AdminUserRow
import com.opengate.app.data.model.Platform
import com.opengate.app.data.model.Role
import com.opengate.app.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val myPermissions: Set<String> = emptySet(),
    val myRoleKey: String = "",
    val myRoleName: String = "",
    val isOwner: Boolean = false,
    val platforms: List<Platform> = emptyList(),
    val admins: List<AdminUserRow> = emptyList(),
    val roles: List<Role> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null
) {
    val canManagePlatforms get() = isOwner   // المنصات: لمالك المشروع فقط (مطابق لسياسات القاعدة)
    val canManageCategories get() = isOwner || myPermissions.contains("categories.manage")
    // إدارة المسؤولين والصلاحيات وتجميدها حصري لمالك المشروع فقط
    val canManageAdmins get() = isOwner
    val canModerate get() = isOwner || myPermissions.contains("listings.approve")
    val canManageContent get() = isOwner || myPermissions.contains("content.manage")
}

class AdminViewModel(private val repo: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val adminStatus = repo.getMyAdminStatus()
                val roleKey = adminStatus?.role_key ?: ""
                val isOwner = roleKey == "owner"
                val roleName = if (isOwner) "مالك المشروع" else adminStatus?.role_name ?: "مشرف عام"

                val perms = repo.getMyPermissionKeys()
                val platforms = repo.getAllPlatformsForAdmin()
                val roles = repo.getAllRoles()
                val admins = if (isOwner) repo.getAdmins() else emptyList()

                _state.value = _state.value.copy(
                    myPermissions = perms,
                    myRoleKey = roleKey,
                    myRoleName = roleName,
                    isOwner = isOwner,
                    platforms = platforms,
                    roles = roles,
                    admins = admins,
                    loading = false
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = com.opengate.app.ui.components.sanitizeErrorMessage(it))
            }
        }
    }

    fun togglePlatform(platform: Platform) {
        viewModelScope.launch {
            val newVisible = !platform.is_visible
            runCatching { repo.setPlatformVisibility(platform.id, newVisible) }
                .onSuccess {
                    com.opengate.app.data.platforms.PlatformsStore.refresh()
                    load()
                    _state.value = _state.value.copy(
                        message = if (newVisible) "تم إظهار منصة «${platform.name_ar}» بنجاح" else "تم إخفاء منصة «${platform.name_ar}» بنجاح"
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it))
                }
        }
    }

    fun addPlatform(name: String, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val key = "pf_" + System.currentTimeMillis()
            runCatching {
                repo.createPlatform(key, name.trim(), icon.ifBlank { "🏷️" })
            }.onSuccess { categoryCreated ->
                com.opengate.app.data.platforms.PlatformsStore.refresh()
                load()
                _state.value = _state.value.copy(
                    message = if (categoryCreated) "تمت إضافة منصة «$name» بنجاح وتفعيل ظهورها"
                    else "تمت إضافة منصة «$name»، لكن تعذّر إنشاء تصنيف افتراضي — أضف تصنيفاً من شاشة التصنيفات ليتمكن المستخدمون من النشر"
                )
            }.onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun addAdmin(email: String, roleId: String) {
        if (email.isBlank() || roleId.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.addAdminByEmail(email.trim(), roleId) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(message = "تم منح الصلاحيات وتعيين المشرف بنجاح")
                }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun updateAdminRole(admin: AdminUserRow, newRoleId: String) {
        viewModelScope.launch {
            runCatching { repo.updateAdminRole(admin.id, newRoleId) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(message = "تم تعديل وتحديث دور المشرف بنجاح")
                }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun toggleAdminActive(admin: AdminUserRow) {
        viewModelScope.launch {
            val newActive = !admin.is_active
            runCatching { repo.setAdminActive(admin.id, newActive) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(
                        message = if (newActive) "تم تنشيط وتفعيل حساب المشرف" else "تم تجميد حساب المشرف وتعطيل صلاحياته مؤقتاً ❄️"
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    fun removeAdmin(admin: AdminUserRow) {
        viewModelScope.launch {
            runCatching { repo.removeAdmin(admin.id) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(message = "تم سحب وحذف كافة صلاحيات الإشراف عن الحساب بنجاح")
                }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }

    suspend fun getAdminPermissions(admin: AdminUserRow): Set<String> {
        return repo.getAdminEffectivePermissions(admin)
    }

    fun updateAdminPermissions(admin: AdminUserRow, grantedKeys: Set<String>) {
        viewModelScope.launch {
            runCatching { repo.saveAdminPermissions(admin.id, grantedKeys) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(message = "تم حفظ وتحديث صلاحيات المشرف بنجاح ✓")
                }
                .onFailure { _state.value = _state.value.copy(error = com.opengate.app.ui.components.sanitizeErrorMessage(it)) }
        }
    }
}
