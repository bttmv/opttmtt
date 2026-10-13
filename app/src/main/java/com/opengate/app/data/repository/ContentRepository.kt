package com.opengate.app.data.repository

import com.opengate.app.data.model.FeaturedSponsor
import com.opengate.app.data.model.SocialLink
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/** قراءة المحتوى العام للشاشة الرئيسية. سياسات RLS تضمن أن غير المفعّل لا يصل لغير الإدارة أصلاً. */
class ContentRepository(private val client: SupabaseClient) {

    suspend fun getVisibleSponsors(): List<FeaturedSponsor> =
        client.postgrest["featured_sponsors"]
            .select { filter { eq("is_visible", true) }; order("sort_order", Order.ASCENDING) }
            .decodeList()

    suspend fun getVisibleSocialLinks(): List<SocialLink> =
        client.postgrest["social_links"]
            .select { filter { eq("is_visible", true) }; order("sort_order", Order.ASCENDING) }
            .decodeList<SocialLink>()
            .filter { it.value.isNotBlank() }
}
