package com.opengate.app.data.repository

import com.opengate.app.data.model.RatingSummary
import com.opengate.app.data.model.Review
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/**
 * تقييمات ومراجعات من الزوار المسجّلين، المشترين، وأي مستخدم — تدعم
 * تقييم بائع/تاجر/مكتب بشكل عام (reviewee_id) أو تقييم إعلان محدد (listing_id).
 */
class ReviewRepository(private val client: SupabaseClient) {

    /** كل مراجعات إعلان معيّن، الأحدث أولاً */
    suspend fun getListingReviews(listingId: String): List<Review> =
        client.postgrest["reviews"]
            .select { filter { eq("listing_id", listingId) }; order("created_at", Order.DESCENDING) }
            .decodeList()

    /** كل مراجعات بائع/تاجر معيّن (تُعرض في صفحة ملفه العام) */
    suspend fun getSellerReviews(sellerId: String): List<Review> =
        client.postgrest["reviews"]
            .select { filter { eq("reviewee_id", sellerId) }; order("created_at", Order.DESCENDING) }
            .decodeList()

    /** متوسط تقييم البائع وعدد المراجعات — من الـ view الجاهز seller_rating_summary */
    suspend fun getSellerRatingSummary(sellerId: String): RatingSummary =
        client.postgrest["seller_rating_summary"]
            .select { filter { eq("user_id", sellerId) } }
            .decodeSingleOrNull() ?: RatingSummary()

    suspend fun getListingRatingSummary(listingId: String): RatingSummary =
        client.postgrest["listing_rating_summary"]
            .select { filter { eq("listing_id", listingId) } }
            .decodeSingleOrNull() ?: RatingSummary()

    /**
     * إضافة تقييم جديد. صف المقيِّم في profiles يُنشأ تلقائياً عند التسجيل (trigger في القاعدة)،
     * ومنع تقييم إعلانك أو تكرار التقييم مفروض في القاعدة أيضاً.
     * (كان الكود السابق يعمل upsert على profiles فيُعيد نوع حساب المستخدم إلى "individual" — أُزيل.)
     */
    suspend fun addReview(review: Review): Review =
        client.postgrest["reviews"].insert(review) { select() }.decodeSingle()

    suspend fun deleteReview(reviewId: String) {
        client.postgrest["reviews"].delete { filter { eq("id", reviewId) } }
    }
}
