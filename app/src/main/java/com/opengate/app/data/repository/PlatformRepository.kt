package com.opengate.app.data.repository

import com.opengate.app.data.model.Category
import com.opengate.app.data.model.CategoryFieldSchema
import com.opengate.app.data.model.Platform
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class PlatformRepository(private val client: SupabaseClient) {

    /** المنصات الظاهرة من الخادم؛ عند فشل الشبكة تُستخدم آخر نسخة مخزنة */
    suspend fun getVisiblePlatforms(): List<Platform> = runCatching {
        client.postgrest["platforms"]
            .select { filter { eq("is_visible", true) }; order("sort_order", Order.ASCENDING) }
            .decodeList<Platform>()
    }.getOrDefault(com.opengate.app.data.platforms.PlatformsStore.visiblePlatforms.value)

    suspend fun getPlatformById(id: String): Platform = runCatching {
        client.postgrest["platforms"].select { filter { eq("id", id) } }.decodeSingle<Platform>()
    }.recoverCatching {
        client.postgrest["platforms"].select { filter { eq("key", id) } }.decodeSingle<Platform>()
    }.getOrThrow()

    suspend fun getCategories(platformId: String): List<Category> = runCatching {
        client.postgrest["categories"]
            .select { filter { eq("platform_id", platformId); eq("is_visible", true) } }
            .decodeList<Category>()
    }.getOrDefault(emptyList())

    /** الحقول الديناميكية لتصنيف معيّن — هذا ما يبني به التطبيق النموذج تلقائياً */
    suspend fun getFieldSchemas(categoryId: String): List<CategoryFieldSchema> = runCatching {
        client.postgrest["category_field_schemas"]
            .select { filter { eq("category_id", categoryId) }; order("sort_order", Order.ASCENDING) }
            .decodeList<CategoryFieldSchema>()
    }.getOrDefault(emptyList())
}
