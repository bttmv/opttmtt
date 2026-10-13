package com.opengate.app.data.platforms

import android.content.Context
import android.content.SharedPreferences
import com.opengate.app.data.model.Platform
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * مخزن المنصات المركزي.
 * الخادم (Supabase) هو المصدر الوحيد للحقيقة في ظهور/إخفاء المنصات؛
 * نخزّن آخر قائمة ناجحة فقط كنسخة للعرض دون اتصال. لا توجد منصات وهمية ولا حالة إخفاء محلية.
 */
object PlatformsStore {
    private var prefs: SharedPreferences? = null
    private var supabaseClient: SupabaseClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(Platform.serializer())

    private val _visiblePlatforms = MutableStateFlow<List<Platform>>(emptyList())
    val visiblePlatforms: StateFlow<List<Platform>> = _visiblePlatforms.asStateFlow()

    private val _allPlatforms = MutableStateFlow<List<Platform>>(emptyList())
    val allPlatforms: StateFlow<List<Platform>> = _allPlatforms.asStateFlow()

    fun init(context: Context, client: SupabaseClient) {
        prefs = context.applicationContext.getSharedPreferences("open_gate_platform_cache", Context.MODE_PRIVATE)
        supabaseClient = client
        loadCache()
        refresh()
    }

    private fun loadCache() {
        val raw = prefs?.getString("platforms_json", null) ?: return
        runCatching { json.decodeFromString(listSerializer, raw) }.getOrNull()?.let { apply(it) }
    }

    private fun apply(list: List<Platform>) {
        _allPlatforms.value = list
        _visiblePlatforms.value = list.filter { it.is_visible }
    }

    /** يعيد تحميل المنصات من الخادم (المسؤول يرى المخفية أيضاً بحكم RLS) */
    fun refresh() {
        val client = supabaseClient ?: return
        scope.launch {
            val remote = runCatching {
                client.postgrest["platforms"]
                    .select { order("sort_order", Order.ASCENDING) }
                    .decodeList<Platform>()
            }.getOrNull() ?: return@launch   // فشل الشبكة: نُبقي آخر نسخة معروفة

            apply(remote)
            runCatching {
                prefs?.edit()?.putString("platforms_json", json.encodeToString(listSerializer, remote))?.apply()
            }
        }
    }
}
