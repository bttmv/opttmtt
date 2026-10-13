package com.opengate.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.ui.components.sanitizeErrorMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class AuthMode { LOGIN, SIGNUP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val accountType: String = "individual",  // individual | real_estate_office | car_dealer
    val loading: Boolean = false,
    val success: Boolean = false,
    val needsEmailConfirmation: Boolean = false,
    val error: String? = null
)

class AuthViewModel(private val client: SupabaseClient) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun toggleMode() {
        _state.value = _state.value.copy(
            mode = if (_state.value.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN,
            error = null
        )
    }

    fun onEmailChange(v: String) { _state.value = _state.value.copy(email = v) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v) }
    fun onFullNameChange(v: String) { _state.value = _state.value.copy(fullName = v) }
    fun onAccountTypeChange(v: String) { _state.value = _state.value.copy(accountType = v) }

    fun continueAsGuest() {
        _state.value = _state.value.copy(success = true)
    }

    fun submit() {
        val s = _state.value
        val cleanEmail = s.email
            .replace("\u00A0", "")
            .replace("\u200B", "")
            .trim()
            .replace("\\s+".toRegex(), "")
            .lowercase()
        val cleanPassword = s.password
        val cleanFullName = s.fullName.trim()

        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _state.value = s.copy(error = "يرجى إدخال بريد إلكتروني صالح (مثال: name@example.com)")
            return
        }
        if (cleanPassword.isEmpty()) {
            _state.value = s.copy(error = "يرجى إدخال كلمة المرور")
            return
        }
        // قاعدة الطول تُطبَّق عند التسجيل فقط حتى لا نحجب دخول حسابات قديمة بكلمات أقصر
        if (s.mode == AuthMode.SIGNUP && cleanPassword.length < MIN_PASSWORD_LENGTH) {
            _state.value = s.copy(error = "كلمة المرور يجب ألا تقل عن $MIN_PASSWORD_LENGTH أحرف")
            return
        }
        if (s.mode == AuthMode.SIGNUP && (cleanPassword.all { it.isDigit() } || cleanPassword.all { it.isLetter() })) {
            _state.value = s.copy(error = "استخدم في كلمة المرور حروفاً وأرقاماً معاً")
            return
        }
        if (s.mode == AuthMode.SIGNUP && cleanFullName.isBlank()) {
            _state.value = s.copy(error = "يرجى إدخال الاسم الكامل")
            return
        }

        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                if (s.mode == AuthMode.LOGIN) {
                    client.auth.signInWith(Email) { email = cleanEmail; password = cleanPassword }
                } else {
                    // الملف الشخصي يُنشأ تلقائياً في القاعدة (trigger handle_new_user) من هذه البيانات،
                    // فيعمل حتى مع تفعيل تأكيد البريد، ولا يستطيع العميل رفع صلاحيات حسابه بنفسه.
                    client.auth.signUpWith(Email) {
                        email = cleanEmail
                        password = cleanPassword
                        data = buildJsonObject {
                            put("full_name", cleanFullName)
                            put("account_type", s.accountType)
                        }
                    }
                }
            }.onSuccess {
                val confirmed = client.auth.currentUserOrNull() != null
                _state.value = _state.value.copy(
                    loading = false,
                    success = confirmed,
                    needsEmailConfirmation = !confirmed
                )
            }.onFailure { ex ->
                val raw = ex.message.orEmpty()
                val errorMsg = when {
                    raw.contains("unable to validate email", ignoreCase = true) ||
                    raw.contains("invalid format", ignoreCase = true) ->
                        "صيغة البريد الإلكتروني غير مقبولة، يرجى كتابة بريد صالح (مثال: name@example.com)"
                    raw.contains("Invalid login credentials", ignoreCase = true) ->
                        "بيانات الدخول غير صحيحة. يرجى التأكد من البريد وكلمة المرور."
                    raw.contains("Email not confirmed", ignoreCase = true) ->
                        "لم يتم تأكيد بريدك بعد. افتح رسالة التأكيد المرسلة إلى بريدك ثم سجّل الدخول."
                    raw.contains("User already registered", ignoreCase = true) ->
                        "هذا الحساب مسجّل مسبقاً. يمكنك التبديل لتسجيل الدخول."
                    raw.contains("rate limit", ignoreCase = true) || raw.contains("too many", ignoreCase = true) ->
                        "محاولات كثيرة خلال وقت قصير. انتظر قليلاً ثم حاول مجدداً."
                    raw.contains("password", ignoreCase = true) && raw.contains("weak", ignoreCase = true) ->
                        "كلمة المرور ضعيفة. استخدم كلمة أطول وأصعب في التخمين."
                    else -> sanitizeErrorMessage(ex)
                }
                _state.value = _state.value.copy(loading = false, error = errorMsg)
            }
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
