package com.opengate.app.data.favorites

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteRow(
    val user_id: String,
    val listing_id: String
)

/**
 * مخزن المفضلة:
 * - تُحفظ المفضلة محلياً **لكل مستخدم على حدة** (مفتاح مستقل لكل حساب + مفتاح للزائر)،
 *   فلا تظهر مفضلة حساب في حساب آخر على نفس الجهاز.
 * - عند تسجيل الدخول/تبديل الحساب تُحمَّل مفضلة المستخدم من الخادم (المصدر الوحيد للحقيقة).
 * - التغيير محلي فوراً ثم يُزامن مع جدول favorites، ويُتراجع عنه إن رفضه الخادم.
 */
object FavoritesStore {
    private const val KEY_GUEST = "favorite_ids_guest"
    private const val LEGACY_KEY = "favorite_ids"

    private var prefs: SharedPreferences? = null
    private var client: SupabaseClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentKey: String = KEY_GUEST

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    fun init(context: Context, supabaseClient: SupabaseClient) {
        prefs = context.applicationContext.getSharedPreferences("open_gate_favorites", Context.MODE_PRIVATE)
        client = supabaseClient
        // المفتاح القديم كان مشتركاً بين كل الحسابات؛ يُحذف (المفضلة المزامنة تعود من الخادم)
        prefs?.edit()?.remove(LEGACY_KEY)?.apply()

        scope.launch {
            runCatching { supabaseClient.auth.awaitInitialization() }
            supabaseClient.auth.sessionStatus.collect {
                onUserChanged(supabaseClient.auth.currentUserOrNull()?.id)
            }
        }
    }

    private fun keyFor(userId: String?) = if (userId == null) KEY_GUEST else "favorite_ids_$userId"

    private suspend fun onUserChanged(userId: String?) {
        val key = keyFor(userId)
        currentKey = key
        _favoriteIds.value = prefs?.getStringSet(key, emptySet())?.toSet() ?: emptySet()
        if (userId != null) refreshFromRemote(userId, key)
    }

    private suspend fun refreshFromRemote(userId: String, key: String) {
        val c = client ?: return
        val remote = runCatching {
            c.postgrest["favorites"]
                .select { filter { eq("user_id", userId) } }
                .decodeList<FavoriteRow>()
                .map { it.listing_id }
                .toSet()
        }.getOrNull() ?: return   // فشل الشبكة: نُبقي النسخة المحلية

        if (currentKey == key) {
            _favoriteIds.value = remote
            prefs?.edit()?.putStringSet(key, remote)?.apply()
        }
    }

    fun isFavorite(listingId: String?): Boolean {
        if (listingId == null) return false
        return _favoriteIds.value.contains(listingId)
    }

    private fun setLocal(key: String, ids: Set<String>) {
        if (currentKey == key) _favoriteIds.value = ids
        prefs?.edit()?.putStringSet(key, ids)?.apply()
    }

    fun toggleFavorite(listingId: String?) {
        if (listingId == null) return
        val key = currentKey
        val before = _favoriteIds.value
        val willBeFavorite = !before.contains(listingId)
        val after = if (willBeFavorite) before + listingId else before - listingId
        setLocal(key, after)

        val c = client ?: return
        val userId = c.auth.currentUserOrNull()?.id ?: return   // الزائر: محلي فقط
        scope.launch {
            val ok = runCatching {
                if (willBeFavorite) {
                    c.postgrest["favorites"].upsert(FavoriteRow(user_id = userId, listing_id = listingId))
                } else {
                    c.postgrest["favorites"].delete {
                        filter {
                            eq("user_id", userId)
                            eq("listing_id", listingId)
                        }
                    }
                }
            }.isSuccess
            if (!ok) {
                // تراجع عن التغيير المحلي حتى لا تختلف المفضلة عن الخادم
                val current = if (currentKey == key) _favoriteIds.value else (prefs?.getStringSet(key, emptySet())?.toSet() ?: emptySet())
                setLocal(key, if (willBeFavorite) current - listingId else current + listingId)
            }
        }
    }
}
