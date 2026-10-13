package com.opengate.app.data.auth

import android.content.Context
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * مدير جلسات يحفظ الجلسة مشفّرة (AES-GCM عبر Android Keystore) داخل SharedPreferences.
 * يبقي المستخدم مسجلاً لدخوله حتى يضغط "تسجيل الخروج"، دون تخزين التوكنات نصاً صريحاً.
 */
class AndroidSharedPrefSessionManager(context: Context) : SessionManager {

    private val prefs = context.applicationContext.getSharedPreferences("opengate_auth_session", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override suspend fun saveSession(session: UserSession) {
        withContext(Dispatchers.IO) {
            runCatching {
                val serialized = json.encodeToString(UserSession.serializer(), session)
                prefs.edit()
                    .putString(KEY_ENCRYPTED_SESSION, SecureStore.encrypt(serialized))
                    .remove(KEY_LEGACY_PLAIN_SESSION) // حذف أي نسخة قديمة غير مشفّرة
                    .commit()
            }
        }
    }

    override suspend fun loadSession(): UserSession? {
        return withContext(Dispatchers.IO) {
            // نسخة قديمة غير مشفّرة: تُحذف ويُطلب تسجيل دخول جديد (أأمن من الاحتفاظ بها)
            if (prefs.contains(KEY_LEGACY_PLAIN_SESSION)) {
                prefs.edit().remove(KEY_LEGACY_PLAIN_SESSION).commit()
            }
            val encrypted = prefs.getString(KEY_ENCRYPTED_SESSION, null) ?: return@withContext null
            val serialized = SecureStore.decrypt(encrypted)
            if (serialized == null) {
                prefs.edit().remove(KEY_ENCRYPTED_SESSION).commit()
                return@withContext null
            }
            runCatching { json.decodeFromString(UserSession.serializer(), serialized) }.getOrNull()
        }
    }

    override suspend fun deleteSession() {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .remove(KEY_ENCRYPTED_SESSION)
                .remove(KEY_LEGACY_PLAIN_SESSION)
                .commit()
        }
    }

    companion object {
        private const val KEY_ENCRYPTED_SESSION = "key_active_user_session_enc"
        private const val KEY_LEGACY_PLAIN_SESSION = "key_active_user_session"
    }
}
