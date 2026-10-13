package com.opengate.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Platform(
    val id: String,
    val key: String,
    val name_ar: String,
    val icon: String? = null,
    val is_visible: Boolean = true,
    val sort_order: Int = 0,
    val requires_moderation: Boolean = false,
    val is_auction_platform: Boolean = false
)

@Serializable
data class Category(
    val id: String,
    val platform_id: String,
    val key: String,
    val name_ar: String,
    val is_visible: Boolean = true
)

@Serializable
data class CategoryFieldSchema(
    val id: String,
    val category_id: String,
    val field_key: String,
    val label_ar: String,
    val field_type: String,       // text | number | boolean | select | multi_select | date | phone | url | textarea
    val is_required: Boolean = false,
    val options: List<String>? = null,
    val sort_order: Int = 0
)

@Serializable
data class Listing(
    val id: String? = null,
    val owner_id: String,
    val platform_id: String,
    val category_id: String,
    val title: String,
    val description: String? = null,
    val price: Double? = null,
    val currency: String = "YER",
    val city: String? = null,
    val contact_phone: String? = null,
    val attributes: Map<String, JsonElement> = emptyMap(),
    val status: String = "draft",   // draft | pending_review | published | rejected | sold | expired | archived
    /** سبب الرفض (يقرؤه صاحب الإعلان فقط؛ يضبطه المراجع في الخادم) */
    val rejection_reason: String? = null
)

@Serializable
data class ListingImage(
    val id: String? = null,
    val listing_id: String,
    val url: String,
    val sort_order: Int = 0
)

/** تقييم بائع/تاجر عام أو تقييم إعلان محدد — مطابق لجدول reviews */
@Serializable
data class Review(
    val id: String? = null,
    val reviewer_id: String,
    val reviewee_id: String? = null,
    val listing_id: String? = null,
    val rating: Int,               // 1..5
    val comment: String? = null,
    val created_at: String? = null
)

/** يقابل الـ view: seller_rating_summary / listing_rating_summary */
@Serializable
data class RatingSummary(
    val average_rating: Double = 0.0,
    val reviews_count: Int = 0
)

/** تقابل جدول conversations */
@Serializable
data class Conversation(
    val id: String? = null,
    val listing_id: String? = null,
    val buyer_id: String,
    val seller_id: String,
    val created_at: String? = null
)

/** نسخة مع اسم الإعلان مدمجاً (Embed) — تُستخدم في قائمة المحادثات */
@Serializable
data class ConversationRow(
    val id: String,
    val listing_id: String? = null,
    val buyer_id: String,
    val seller_id: String,
    val created_at: String? = null,
    val listings: ListingTitleRef? = null
)

@Serializable
data class ListingTitleRef(val title: String)

/** تقابل جدول messages — الدردشة الفورية عبر Realtime */
@Serializable
data class Message(
    val id: String? = null,
    val conversation_id: String,
    val sender_id: String,
    val content: String? = null,
    val attachment_url: String? = null,
    val is_read: Boolean = false,
    val created_at: String? = null
)

/** تقابل جدول auctions */
@Serializable
data class Auction(
    val id: String? = null,
    val listing_id: String,
    val starting_price: Double,
    val min_increment: Double = 1.0,
    val current_price: Double? = null,
    val starts_at: String,
    val ends_at: String,
    val status: String = "scheduled" // scheduled | live | ended | cancelled
)

/** نسخة مع اسم الإعلان مدمجاً — لعرض قائمة المزادات المباشرة */
@Serializable
data class AuctionRow(
    val id: String,
    val listing_id: String,
    val starting_price: Double,
    val min_increment: Double = 1.0,
    val current_price: Double? = null,
    val starts_at: String,
    val ends_at: String,
    val status: String = "scheduled",
    val listings: ListingTitleRef? = null
)

/** تقابل جدول bids */
@Serializable
data class Bid(
    val id: String? = null,
    val auction_id: String,
    val bidder_id: String,
    val amount: Double,
    val created_at: String? = null
)

@Serializable
data class Profile(
    val id: String,
    val full_name: String? = null,
    val phone: String? = null,
    val avatar_url: String? = null,
    val account_type: String = "individual", // individual | real_estate_office | car_dealer | business
    val business_name: String? = null,
    val business_license_no: String? = null,
    val is_verified: Boolean = false
)

/** يقابل الـ view: my_admin_status — نتيجة فارغة تعني أن المستخدم الحالي ليس مسؤولاً */
@Serializable
data class AdminStatus(
    val admin_user_id: String,
    val user_id: String,
    val role_key: String,
    val role_name: String,
    val is_active: Boolean
)

@Serializable
data class Role(
    val id: String,
    val key: String,
    val name_ar: String,
    val is_system_role: Boolean = false
)

/** صف admin_users مع اسم الدور مدمجاً — لعرضه في شاشة إدارة المسؤولين */
@Serializable
data class AdminUserRow(
    val id: String,
    val user_id: String,
    val role_id: String,
    val is_active: Boolean,
    val roles: Role? = null
)

@Serializable
data class Permission(
    val id: String,
    val key: String,
    val name_ar: String
)

@Serializable
data class SystemPermissionInfo(
    val key: String,
    val title: String,
    val description: String,
    val category: String
)

val SYSTEM_PERMISSIONS = listOf(
    SystemPermissionInfo("platforms.manage", "إدارة المنصات وأقسام السوق", "إظهار وإخفاء المنصات وإضافة منصات جديدة للمتجر", "المنصات والأقسام"),
    SystemPermissionInfo("categories.manage", "إدارة التصنيفات والحقول", "إضافة وتعديل التصنيفات والحقول الإجبارية والاختيارية لكل قسم", "التصنيفات"),
    SystemPermissionInfo("listings.approve", "مراجعة واعتماد الإعلانات", "الموافقة على الإعلانات المعلقة وقبولها أو رفضها مع كتابة السبب", "الإعلانات والمحتوى"),
    SystemPermissionInfo("listings.remove", "حذف الإعلانات المخالفة", "حذف أي إعلان معروض في المنصة وإزالته من المتجر فوراً", "الإعلانات والمحتوى"),
    SystemPermissionInfo("auctions.manage", "إدارة المزادات المباشرة", "التحكم في بدء المزادات، تمديد الوقت، وإلغاء أو ترسية المزادات", "المزادات"),
    SystemPermissionInfo("content.manage", "إدارة الشريط الإعلاني وروابط التواصل", "تحديث الشريط المتحرك بالرئيسية وروابط الدعم والتواصل", "المحتوى والإعلانات"),
    SystemPermissionInfo("users.manage", "إدارة حسابات المستخدمين", "تعديل بيانات المستخدمين وحذف الحسابات المخالفة وتوثيقها", "المستخدمين والأمان"),
    SystemPermissionInfo("admins.manage", "إدارة وتعيين المشرفين", "إضافة مشرفين جدد وتعديل صلاحياتهم وتجميد عضوياتهم", "الإدارة العليا")
)

/** يقابل جدول social_links — حسابات التواصل (فارغة ومخفية حتى يفعّلها المالك) */
@Serializable
data class SocialLink(
    val key: String,
    val label_ar: String,
    val value: String = "",
    val is_visible: Boolean = false,
    val sort_order: Int = 0
)

/** يقابل جدول featured_sponsors — عناصر الشريط المتحرك في الرئيسية */
@Serializable
data class FeaturedSponsor(
    val id: String,
    val text_ar: String,
    val is_visible: Boolean = true,
    val sort_order: Int = 0
)
