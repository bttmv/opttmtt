package com.opengate.app.di

import android.content.Context
import com.opengate.app.data.auth.AndroidSharedPrefSessionManager
import com.opengate.app.data.settings.SettingsStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

/**
 * نقطة إنشاء عميل Supabase الوحيدة في التطبيق (Singleton).
 * تهيئة الجلسة للتخزين الدائم على ذاكرة الهاتف عبر AndroidSharedPrefSessionManager
 * وتجاهل أي حقول غير معروفة تلقائياً لضمان عدم فشل قراءة المنصات والجداول.
 */
object SupabaseProvider {

    @Volatile private var instance: SupabaseClient? = null

    fun isConfigured(): Boolean = SettingsStore.isSupabaseConfigured()

    fun resetClient() {
        synchronized(this) {
            instance = null
        }
    }

    fun client(context: Context): SupabaseClient =
        instance ?: synchronized(this) {
            val url = SettingsStore.getEffectiveSupabaseUrl().let {
                if (it.isBlank() || !it.startsWith("http")) "https://placeholder.supabase.co" else it
            }
            val key = SettingsStore.getEffectiveSupabaseKey().ifBlank { "placeholder-anon-key" }

            val appContext = context.applicationContext

            instance ?: createSupabaseClient(
                supabaseUrl = url,
                supabaseKey = key
            ) {
                defaultSerializer = KotlinXSerializer(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                })
                install(Auth) {
                    sessionManager = AndroidSharedPrefSessionManager(appContext)
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                }
                install(Postgrest)  // قراءة/كتابة الجداول
                install(Realtime)   // الدردشة الفورية والمزادات
                install(Storage)    // رفع الصور
            }.also { instance = it }
        }
}
