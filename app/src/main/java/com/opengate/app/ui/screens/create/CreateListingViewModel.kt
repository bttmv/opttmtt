package com.opengate.app.ui.screens.create

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opengate.app.data.model.Category
import com.opengate.app.data.model.CategoryFieldSchema
import com.opengate.app.data.model.Listing
import com.opengate.app.data.model.Platform
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.data.repository.PlatformRepository
import com.opengate.app.ui.components.isNetworkError
import com.opengate.app.ui.components.sanitizeErrorMessage
import com.opengate.app.util.PhoneUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** الحد الأدنى والأقصى لصور الإعلان الواحد */
const val MIN_LISTING_IMAGES = 1
const val MAX_LISTING_IMAGES = 8

data class CreateListingUiState(
    val platforms: List<Platform> = emptyList(),
    val categories: List<Category> = emptyList(),
    val fieldSchemas: List<CategoryFieldSchema> = emptyList(),

    val selectedPlatform: Platform? = null,
    val selectedCategory: Category? = null,
    val customCategoryName: String = "",
    val isCustomCategory: Boolean = false,

    val title: String = "",
    val description: String = "",
    val price: String = "",
    val currency: String = "YER",
    val city: String = "",
    val contactPhone: String = "",
    val dynamicValues: Map<String, String> = emptyMap(),
    val images: List<Uri> = emptyList(),

    val loadingCategories: Boolean = false,
    val loadingFields: Boolean = false,
    val submitting: Boolean = false,
    val uploadingImages: Boolean = false,
    val submitted: Boolean = false,
    val offlineQueued: Boolean = false,
    /** رسالة تُعرض بعد الإرسال (قيد المراجعة / فشل بعض الصور / حفظ أوفلاين) */
    val postSubmitMessage: String? = null,
    val error: String? = null
)

private const val MAX_TITLE_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 5000
private val ALLOWED_CURRENCIES = setOf("YER", "USD", "SAR")

class CreateListingViewModel(
    private val client: SupabaseClient,
    private val platformRepo: PlatformRepository,
    private val listingRepo: ListingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateListingUiState())
    val state: StateFlow<CreateListingUiState> = _state.asStateFlow()

    // التصنيفات الحقيقية المسجلة في القاعدة للمنصة المختارة (لضمان سلامة Foreign Key)
    private var dbCategories = listOf<Category>()

    init {
        viewModelScope.launch {
            com.opengate.app.data.platforms.PlatformsStore.visiblePlatforms.collect { visibleList ->
                // منصة المزادات لها مسار إنشاء خاص (مزاد + إعلان ذرّياً) من شاشة المزادات
                _state.update { it.copy(platforms = visibleList.filter { p -> !p.is_auction_platform }) }
            }
        }
        if (client.auth.currentUserOrNull() != null) {
            viewModelScope.launch {
                // الهاتف لا يظهر في profiles العامة؛ نقرأه من عرض my_profile الخاص بصاحب الحساب
                runCatching {
                    client.postgrest["my_profile"].select().decodeSingleOrNull<com.opengate.app.data.model.Profile>()
                }.onSuccess { p ->
                    if (!p?.phone.isNullOrBlank() && _state.value.contactPhone.isBlank()) {
                        _state.update { it.copy(contactPhone = p?.phone ?: "") }
                    }
                }
            }
        }
    }

    /** التصنيف الحقيقي (UUID موجود في القاعدة) الذي سيُربط به الإعلان؛ null إن لم يتوفر */
    private fun resolveCategoryId(category: Category): String? =
        if (category.id.startsWith("custom_")) {
            (dbCategories.firstOrNull { it.key == "other" || it.key == "general" } ?: dbCategories.firstOrNull())?.id
        } else {
            category.id
        }

    /** يُستدعى عند اختيار المستخدم منصة — يجلب تصنيفاتها الحقيقية من القاعدة */
    fun onPlatformSelected(platform: Platform) {
        _state.update {
            it.copy(
                selectedPlatform = platform,
                selectedCategory = null,
                customCategoryName = "",
                isCustomCategory = false,
                fieldSchemas = emptyList(),
                dynamicValues = emptyMap(),
                loadingCategories = true,
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { platformRepo.getCategories(platform.id) }
                .onSuccess { list ->
                    dbCategories = list
                    val withCustom = if (list.isEmpty()) list else list + Category(
                        id = "custom_${platform.id}",
                        platform_id = platform.id,
                        key = "custom",
                        name_ar = "✏️ تصنيف آخر (كتابة يدوية حرة)"
                    )
                    _state.update {
                        it.copy(
                            categories = withCustom,
                            loadingCategories = false,
                            error = if (list.isEmpty()) "لا توجد تصنيفات متاحة لهذه المنصة حالياً" else null
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = sanitizeErrorMessage(e), loadingCategories = false) }
                }
        }
    }

    /** يُستدعى عند اختيار التصنيف */
    fun onCategorySelected(category: Category) {
        val isCustom = category.key == "custom"
        _state.update {
            it.copy(selectedCategory = category, isCustomCategory = isCustom, dynamicValues = emptyMap(), loadingFields = true)
        }
        viewModelScope.launch {
            val targetId = resolveCategoryId(category) ?: category.id
            runCatching { platformRepo.getFieldSchemas(targetId) }
                .onSuccess { list ->
                    // الاكتفاء بحقل الموديل وحذف سنة الصنع لتفادي التكرار وإرباك المستخدم
                    val isCarPlatform = _state.value.selectedPlatform?.key == "cars"
                    val filteredList = if (isCarPlatform) {
                        list.filterNot { it.field_key == "year" || it.label_ar.contains("سنة الصنع") }
                    } else list

                    // إن لم يكن للتصنيف حقول مسجلة نضيف حقولاً افتراضية بسيطة
                    val finalFields = if (filteredList.isEmpty()) {
                        listOf(
                            CategoryFieldSchema(
                                id = "def_condition",
                                category_id = category.id,
                                field_key = "condition_detail",
                                label_ar = "حالة السلعة (جديد / مستخدم / مجدد)",
                                field_type = "text",
                                is_required = false,
                                sort_order = 1
                            ),
                            CategoryFieldSchema(
                                id = "def_model",
                                category_id = category.id,
                                field_key = "model_spec",
                                label_ar = "الموديل أو المواصفات الفنية",
                                field_type = "text",
                                is_required = false,
                                sort_order = 2
                            )
                        )
                    } else {
                        filteredList
                    }
                    _state.update { it.copy(fieldSchemas = finalFields, loadingFields = false) }
                }
                .onFailure { _state.update { it.copy(loadingFields = false) } }
        }
    }

    fun onCustomCategoryNameChange(v: String) { _state.update { it.copy(customCategoryName = v) } }
    fun onTitleChange(v: String) { _state.update { it.copy(title = v) } }
    fun onDescriptionChange(v: String) { _state.update { it.copy(description = v) } }
    fun onPriceChange(v: String) { _state.update { it.copy(price = v) } }
    fun onCurrencyChange(v: String) { _state.update { it.copy(currency = v) } }
    fun onCityChange(v: String) { _state.update { it.copy(city = v) } }
    fun onContactPhoneChange(v: String) { _state.update { it.copy(contactPhone = v) } }

    fun onImagesPicked(uris: List<Uri>) {
        _state.update { it.copy(images = (it.images + uris).distinct().take(MAX_LISTING_IMAGES), error = null) }
    }

    fun removeImage(uri: Uri) {
        _state.update { it.copy(images = it.images.filterNot { u -> u == uri }) }
    }

    fun onDynamicValueChange(fieldKey: String, value: String) {
        _state.update { it.copy(dynamicValues = it.dynamicValues + (fieldKey to value)) }
    }

    /** ينشئ الإعلان (طلب نشر؛ الخادم يقرر منشور/قيد المراجعة) ثم يرفع صوره */
    fun submit(readBytes: suspend (Uri) -> ByteArray) {
        val s = _state.value
        if (s.submitting) return
        val userId = client.auth.currentUserOrNull()?.id
        val platform = s.selectedPlatform
        val category = s.selectedCategory

        fun fail(msg: String) { _state.update { it.copy(error = msg) } }

        if (userId == null) return fail("يجب تسجيل الدخول بحسابك أولاً لتتمكن من إضافة إعلان")
        if (platform == null) return fail("يرجى اختيار المنصة المناسبة للإعلان")
        if (category == null) return fail("يرجى اختيار التصنيف")
        if (s.isCustomCategory && s.customCategoryName.isBlank()) return fail("يرجى كتابة اسم التصنيف المخصص")
        if (s.title.isBlank()) return fail("يرجى كتابة عنوان الإعلان")
        if (s.title.trim().length > MAX_TITLE_LENGTH) return fail("العنوان طويل جداً (الحد الأقصى $MAX_TITLE_LENGTH حرف)")
        if (s.description.trim().length > MAX_DESCRIPTION_LENGTH) return fail("الوصف طويل جداً (الحد الأقصى $MAX_DESCRIPTION_LENGTH حرف)")
        if (s.currency !in ALLOWED_CURRENCIES) return fail("عملة غير مدعومة")
        s.fieldSchemas.firstOrNull { it.is_required && s.dynamicValues[it.field_key].isNullOrBlank() }
            ?.let { return fail("يرجى تعبئة الحقل الإلزامي: ${it.label_ar}") }

        val priceText = PhoneUtils.toAsciiDigits(s.price).replace(",", "").replace("٫", ".").trim()
        val priceValue = if (priceText.isBlank()) 0.0 else priceText.toDoubleOrNull()
        if (priceValue == null || priceValue < 0 || priceValue >= 1_000_000_000_000.0) return fail("يرجى إدخال سعر صحيح")

        val phoneRaw = s.contactPhone.trim()
        if (phoneRaw.isNotEmpty() && !PhoneUtils.isValid(phoneRaw)) return fail("رقم التواصل غير صالح (7 إلى 15 رقماً)")
        val phone = if (phoneRaw.isEmpty()) null else PhoneUtils.normalize(phoneRaw)

        val safeCategoryId = resolveCategoryId(category) ?: return fail("لا يوجد تصنيف صالح لهذه المنصة")
        val displayCategoryName = if (s.isCustomCategory) s.customCategoryName.trim() else category.name_ar
        val city = s.city.trim().ifBlank { "اليمن" }
        val description = s.description.trim().ifBlank { null }

        val stringAttrs = s.dynamicValues.toMutableMap().apply {
            put("custom_category", displayCategoryName)
            put("display_category", displayCategoryName)
            if (description != null) put("description", description)
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            suspend fun saveOffline(reason: String) {
                val imagesBytes = mutableListOf<ByteArray>()
                s.images.forEach { uri ->
                    runCatching { readBytes(uri) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { imagesBytes.add(it) }
                }
                com.opengate.app.data.offline.OfflineSyncManager.enqueueListing(
                    ownerId = userId,
                    platformId = platform.id,
                    categoryId = safeCategoryId,
                    title = s.title.trim(),
                    description = description,
                    price = priceValue,
                    currency = s.currency,
                    city = city,
                    contactPhone = phone,
                    attributes = stringAttrs,
                    imagesBytes = imagesBytes
                )
                _state.update {
                    it.copy(
                        submitting = false, uploadingImages = false, submitted = true, offlineQueued = true,
                        postSubmitMessage = reason
                    )
                }
            }

            val offlineMsg = "⚡ تم حفظ إعلانك أوفلاين بنجاح ✓ سيتم رفعه ونشره تلقائياً فور توفر الإنترنت"

            if (!com.opengate.app.data.offline.NetworkMonitor.isOnline.value) {
                runCatching { saveOffline(offlineMsg) }
                    .onFailure { fail("تعذّر حفظ الإعلان على الجهاز. تأكد من توفر مساحة تخزين.") ; _state.update { it.copy(submitting = false) } }
                return@launch
            }

            val attrs = stringAttrs.mapValues { (_, v) -> JsonPrimitive(v) as kotlinx.serialization.json.JsonElement }

            val created = runCatching {
                listingRepo.createListing(
                    Listing(
                        owner_id = userId,
                        platform_id = platform.id,
                        category_id = safeCategoryId,
                        title = s.title.trim(),
                        description = description,
                        price = priceValue,
                        currency = s.currency,
                        city = city,
                        contact_phone = phone,
                        attributes = attrs,
                        status = "published"   // طلب نشر فقط؛ الخادم يحدد الحالة النهائية
                    )
                )
            }.getOrElse { e ->
                if (isNetworkError(e)) {
                    // انقطاع الشبكة فقط يُحفظ أوفلاين؛ رفض الخادم (بيانات غير صالحة/صلاحيات) يُعرض كخطأ ولا يُخزَّن
                    runCatching { saveOffline(offlineMsg) }
                        .onFailure { _state.update { it.copy(submitting = false, error = sanitizeErrorMessage(e)) } }
                } else {
                    _state.update { it.copy(submitting = false, uploadingImages = false, error = sanitizeErrorMessage(e)) }
                }
                return@launch
            }

            val listingId = created.id
            if (listingId == null) {
                _state.update { it.copy(submitting = false, error = "تعذّر حفظ الإعلان") }
                return@launch
            }

            var failedImages = 0
            if (s.images.isNotEmpty()) {
                _state.update { it.copy(uploadingImages = true) }
                val uploadedUrls = mutableListOf<String>()
                s.images.forEach { uri ->
                    runCatching {
                        val bytes = readBytes(uri)
                        if (bytes.isEmpty()) error("صورة غير صالحة")
                        uploadedUrls.add(listingRepo.uploadListingImage(listingId, bytes))
                    }.onFailure { failedImages++ }
                }
                if (uploadedUrls.isNotEmpty()) {
                    runCatching {
                        val jsonAttrs = buildJsonObject {
                            stringAttrs.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                            put("cover_image", JsonPrimitive(uploadedUrls.first()))
                            put("photos", kotlinx.serialization.json.JsonArray(uploadedUrls.map { JsonPrimitive(it) }))
                        }
                        client.postgrest["listings"].update(buildJsonObject { put("attributes", jsonAttrs) }) {
                            filter { eq("id", listingId) }
                        }
                    }.onFailure { failedImages = s.images.size }
                }
            }

            val message = buildString {
                if (created.status == "pending_review") append("تم إرسال إعلانك للمراجعة، وسيظهر للجميع بعد اعتماده من الإدارة ✓")
                if (failedImages > 0) {
                    if (isNotEmpty()) append("\n")
                    append("تنبيه: تعذّر رفع $failedImages من الصور، يمكنك إعادة نشر الإعلان بصور أخرى.")
                }
            }.ifBlank { null }

            _state.update {
                it.copy(submitting = false, uploadingImages = false, submitted = true, postSubmitMessage = message)
            }
        }
    }
}
