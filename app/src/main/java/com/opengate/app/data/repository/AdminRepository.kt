package com.opengate.app.data.repository

import com.opengate.app.data.model.AdminStatus
import com.opengate.app.data.model.AdminUserRow
import com.opengate.app.data.model.Category
import com.opengate.app.data.model.CategoryFieldSchema
import com.opengate.app.data.model.FeaturedSponsor
import com.opengate.app.data.model.SocialLink
import com.opengate.app.data.model.Platform
import com.opengate.app.data.model.Role
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

/**
 * كل دالة هنا محمية أصلاً بسياسات RLS على مستوى القاعدة (has_permission / is_admin) —
 * حتى لو استُدعيت الدالة من مكان غير متوقع بالتطبيق، القاعدة نفسها ترفض العملية
 * لأي مستخدم لا يملك الصلاحية الفعلية. هذا يطابق مبدأ "لا تثق إلا بالخادم".
 */
class AdminRepository(
    private val client: SupabaseClient
) {

    /**
     * سياسات RLS التي ترفض تعديلاً/حذفاً لا تُرجع خطأ بل "صفر صفوف" — نتحقق من ذلك صراحةً
     * حتى لا تظهر رسالة نجاح كاذبة عندما لا يملك المستخدم الصلاحية فعلاً.
     */
    private fun requireChanged(rows: List<JsonObject>) {
        if (rows.isEmpty()) error("لم تتم العملية — تأكد من امتلاكك الصلاحية المطلوبة")
    }

    /** null تعني: المستخدم الحالي ليس مسؤولاً على الإطلاق (زائر أو مستخدم عادي) */
    suspend fun getMyAdminStatus(): AdminStatus? =
        client.postgrest["my_admin_status"].select().decodeSingleOrNull()

    /** مفاتيح الصلاحيات الفعلية للمستخدم الحالي (دور + تخصيص) — قائمة فارغة لغير المسؤولين */
    suspend fun getMyPermissionKeys(): Set<String> =
        client.postgrest["my_permissions"].select()
            .decodeList<Map<String, String>>()
            .mapNotNull { it["key"] }
            .toSet()

    /** أدوار النظام الفعلية من القاعدة (لا أدوار وهمية). دور المالك لا يُمنح من التطبيق فيُستبعد. */
    suspend fun getAllRoles(): List<Role> =
        client.postgrest["roles"].select().decodeList<Role>().filter { it.key != "owner" }

    /** كل المنصات (ظاهرة ومخفية) — يظهرها فقط لمستخدم يملك is_admin() بحكم سياسة RLS */
    suspend fun getAllPlatformsForAdmin(): List<Platform> =
        client.postgrest["platforms"].select { order("sort_order", io.github.jan.supabase.postgrest.query.Order.ASCENDING) }.decodeList()

    suspend fun setPlatformVisibility(platformId: String, visible: Boolean) {
        requireChanged(
            client.postgrest["platforms"].update(buildJsonObject { put("is_visible", visible) }) {
                select()
                filter { eq("id", platformId) }
            }.decodeList<JsonObject>()
        )
    }

    /** ينشئ منصة جديدة ويحاول إنشاء تصنيف افتراضي لها؛ يرجع true إذا نجح إنشاء التصنيف أيضاً */
    suspend fun createPlatform(key: String, nameAr: String, icon: String): Boolean {
        val platform = client.postgrest["platforms"].insert(
            buildJsonObject {
                put("key", key)
                put("name_ar", nameAr)
                put("icon", icon)
                put("is_visible", true)
            }
        ) { select() }.decodeSingle<Platform>()

        return runCatching {
            client.postgrest["categories"].insert(
                buildJsonObject {
                    put("platform_id", platform.id)
                    put("key", "general")
                    put("name_ar", "عام")
                    put("is_visible", true)
                }
            )
        }.isSuccess
    }

    suspend fun createCategory(platformId: String, key: String, nameAr: String) {
        client.postgrest["categories"].insert(
            buildJsonObject { put("platform_id", platformId); put("key", key); put("name_ar", nameAr) }
        )
    }

    /** يجلب المسؤولين مع اسم دور كل واحد منهم مدمجاً بالاستعلام (Embed) */
    suspend fun getAdmins(): List<AdminUserRow> =
        client.postgrest["admin_users"].select(Columns.raw("*, roles(*)")).decodeList()

    /**
     * إضافة مسؤول جديد. ملاحظة أمنية مهمة: التطبيق لا يقدر (ولا يجب) أن يبحث عن
     * مستخدم ببريده الإلكتروني مباشرة من الجهاز، لأن جدول auth.users غير متاح
     * عبر Postgrest للمستخدمين العاديين حتى لو كانوا مسؤولين. لذلك هذه الدالة
     * تتطلب الـ UUID الخاص بحساب المستخدم (يُنسخ من Supabase Dashboard)، أو
     * تُستبدل لاحقاً بدالة Edge Function آمنة تبحث بالبريد باستخدام صلاحية الخادم.
     */
    /**
     * يبحث عن المستخدم ببريده الإلكتروني عبر دالة SQL محمية (find_user_id_by_email) —
     * تعمل فقط لمن يملك صلاحية admins.manage فعلياً، وترجع null إن لم يوجد الحساب.
     */
    suspend fun findUserIdByEmail(email: String): String? =
        client.postgrest.rpc(
            "find_user_id_by_email",
            buildJsonObject { put("p_email", email.trim()) }
        ).decodeAsOrNull<String>()

    /**
     * إضافة مسؤول جديد بالبريد الإلكتروني مباشرة — الطريقة الموصى بها.
     * يرمي استثناءً واضحاً إن لم يكن للمستخدم حساب بعد (يجب أن يسجّل حسابه في
     * التطبيق أولاً، ثم تضيفه كمسؤول لاحقاً).
     */
    suspend fun addAdminByEmail(email: String, roleId: String) {
        val userId = findUserIdByEmail(email)
            ?: error("لا يوجد حساب بهذا البريد الإلكتروني — اطلب منه إنشاء حساب في التطبيق أولاً")
        addAdmin(userId, roleId)
    }

    suspend fun addAdmin(userId: String, roleId: String) {
        client.postgrest["admin_users"].insert(
            buildJsonObject { put("user_id", userId); put("role_id", roleId); put("is_active", true) }
        )
    }

    suspend fun setAdminActive(adminUserId: String, active: Boolean) {
        requireChanged(
            client.postgrest["admin_users"].update(buildJsonObject { put("is_active", active) }) {
                select()
                filter { eq("id", adminUserId) }
            }.decodeList<JsonObject>()
        )
    }

    suspend fun updateAdminRole(adminUserId: String, newRoleId: String) {
        requireChanged(
            client.postgrest["admin_users"].update(buildJsonObject { put("role_id", newRoleId) }) {
                select()
                filter { eq("id", adminUserId) }
            }.decodeList<JsonObject>()
        )
    }

    suspend fun removeAdmin(adminUserId: String) {
        requireChanged(
            client.postgrest["admin_users"].delete {
                select()
                filter { eq("id", adminUserId) }
            }.decodeList<JsonObject>()
        )
    }

    /** الصلاحيات الفعلية لمشرف (دوره + الاستثناءات) محسوبة في الخادم — للمالك فقط */
    suspend fun getAdminEffectivePermissions(admin: AdminUserRow): Set<String> =
        client.postgrest.rpc(
            "get_admin_permissions",
            buildJsonObject { put("p_admin_user_id", admin.id) }
        ).decodeList<String>().toSet()

    /** يحفظ مجموعة الصلاحيات المختارة لمشرف دفعة واحدة وذرّياً في الخادم — للمالك فقط */
    suspend fun saveAdminPermissions(adminUserId: String, grantedKeys: Set<String>) {
        client.postgrest.rpc(
            "set_admin_permissions",
            buildJsonObject {
                put("p_admin_user_id", adminUserId)
                put("p_granted", buildJsonArray { grantedKeys.forEach { add(it) } })
            }
        )
    }

    // ---------------- التصنيفات والحقول الديناميكية ----------------

    suspend fun getCategoriesForAdmin(platformId: String): List<Category> =
        client.postgrest["categories"].select { filter { eq("platform_id", platformId) } }.decodeList()

    suspend fun setCategoryVisibility(categoryId: String, visible: Boolean) {
        requireChanged(
            client.postgrest["categories"].update(buildJsonObject { put("is_visible", visible) }) {
                select()
                filter { eq("id", categoryId) }
            }.decodeList<JsonObject>()
        )
    }

    suspend fun getFieldsForAdmin(categoryId: String): List<CategoryFieldSchema> =
        client.postgrest["category_field_schemas"].select { filter { eq("category_id", categoryId) } }.decodeList()

    /** يضيف حقلاً ديناميكياً جديداً لتصنيف — يظهر فوراً في نموذج إنشاء الإعلان بدون تحديث التطبيق */
    suspend fun createField(
        categoryId: String, fieldKey: String, labelAr: String,
        fieldType: String, required: Boolean, options: List<String>
    ) {
        client.postgrest["category_field_schemas"].insert(
            buildJsonObject {
                put("category_id", categoryId)
                put("field_key", fieldKey)
                put("label_ar", labelAr)
                put("field_type", fieldType)
                put("is_required", required)
                if (options.isNotEmpty()) put("options", buildJsonArray { options.forEach { add(it) } })
            }
        )
    }

    suspend fun deleteField(fieldId: String) {
        requireChanged(
            client.postgrest["category_field_schemas"].delete {
                select()
                filter { eq("id", fieldId) }
            }.decodeList<JsonObject>()
        )
    }

    // ---------------- المحتوى: حسابات التواصل + الشريط المتحرك ----------------

    suspend fun getSocialLinksForAdmin(): List<SocialLink> =
        client.postgrest["social_links"].select { order("sort_order", Order.ASCENDING) }.decodeList()

    suspend fun updateSocialLink(key: String, value: String, visible: Boolean) {
        requireChanged(
            client.postgrest["social_links"].update(
                buildJsonObject { put("value", value.trim()); put("is_visible", visible) }
            ) {
                select()
                filter { eq("key", key) }
            }.decodeList<JsonObject>()
        )
    }

    suspend fun getSponsorsForAdmin(): List<FeaturedSponsor> =
        client.postgrest["featured_sponsors"].select { order("sort_order", Order.ASCENDING) }.decodeList()

    suspend fun addSponsor(text: String) {
        client.postgrest["featured_sponsors"].insert(buildJsonObject { put("text_ar", text.trim()) })
    }

    suspend fun setSponsorVisible(id: String, visible: Boolean) {
        requireChanged(
            client.postgrest["featured_sponsors"].update(buildJsonObject { put("is_visible", visible) }) {
                select()
                filter { eq("id", id) }
            }.decodeList<JsonObject>()
        )
    }

    suspend fun deleteSponsor(id: String) {
        requireChanged(
            client.postgrest["featured_sponsors"].delete {
                select()
                filter { eq("id", id) }
            }.decodeList<JsonObject>()
        )
    }
}
