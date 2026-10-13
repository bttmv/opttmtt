package com.opengate.app.data.offline

import android.content.Context
import com.opengate.app.data.model.Listing
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.ui.components.isNetworkError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.UUID

@Serializable
data class PendingOfflineListing(
    val localId: String,
    val ownerId: String,
    val platformId: String,
    val categoryId: String,
    val title: String,
    val description: String? = null,
    val price: Double? = null,
    val currency: String = "YER",
    val city: String? = null,
    val contactPhone: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val imageFileNames: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    /** يُحفظ فور إنشاء الإعلان في الخادم حتى لا يتكرر عند إعادة المحاولة */
    val createdListingId: String? = null,
    val uploadedUrls: List<String> = emptyList(),
    /** محاولات فاشلة بسبب رفض الخادم (وليس انقطاع الشبكة) */
    val failedAttempts: Int = 0
)

@Serializable
data class OfflineSyncData(
    val pendingListings: List<PendingOfflineListing> = emptyList()
)

/**
 * مدير طابور المزامنة أوفلاين:
 * - يحفظ الإعلانات المدخلة في وضع عدم الاتصال محلياً على القرص مع حفظ صورها في مجلد التطبيق الداخلي.
 * - يراقب عودة الاتصال ويقوم برفعها تلقائياً وفورياً إلى خوادم Supabase دون أي تدخل يدوي.
 * - يوفر مؤشرات تدفق لحظية (StateFlow) لعدد العناصر المنتظرة وحالة المزامنة.
 */
object OfflineSyncManager {

    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _pendingListings = MutableStateFlow<List<PendingOfflineListing>>(emptyList())
    val pendingListings: StateFlow<List<PendingOfflineListing>> = _pendingListings.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    private fun getStorageFile(): File = File(appContext.filesDir, "open_gate_offline_queue.json")
    private fun getImagesDir(): File {
        val dir = File(appContext.filesDir, "offline_images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    private fun loadFromDisk() {
        scope.launch {
            mutex.withLock {
                val file = getStorageFile()
                if (file.exists()) {
                    runCatching {
                        val content = file.readText()
                        val data = json.decodeFromString<OfflineSyncData>(content)
                        _pendingListings.value = data.pendingListings
                    }
                }
            }
        }
    }

    private fun saveToDiskLocked() {
        runCatching {
            val file = getStorageFile()
            val data = OfflineSyncData(pendingListings = _pendingListings.value)
            val content = json.encodeToString(data)
            val tempFile = File(appContext.filesDir, "open_gate_offline_queue.json.tmp")
            tempFile.writeText(content)
            tempFile.renameTo(file)
        }
    }

    /**
     * حفظ إعلان أوفلاين في طابور الرفع التلقائي مع حفظ صوره محلياً
     */
    suspend fun enqueueListing(
        ownerId: String,
        platformId: String,
        categoryId: String,
        title: String,
        description: String?,
        price: Double?,
        currency: String,
        city: String?,
        contactPhone: String?,
        attributes: Map<String, String>,
        imagesBytes: List<ByteArray>
    ): String = mutex.withLock {
        val localId = "offline_" + UUID.randomUUID().toString()
        val savedImageFileNames = mutableListOf<String>()

        val imagesDir = getImagesDir()
        imagesBytes.forEachIndexed { index, bytes ->
            if (bytes.isNotEmpty()) {
                val fileName = "${localId}_img_${index}.jpg"
                val file = File(imagesDir, fileName)
                runCatching { file.writeBytes(bytes) }
                savedImageFileNames.add(fileName)
            }
        }

        val pending = PendingOfflineListing(
            localId = localId,
            ownerId = ownerId,
            platformId = platformId,
            categoryId = categoryId,
            title = title,
            description = description,
            price = price,
            currency = currency,
            city = city,
            contactPhone = contactPhone,
            attributes = attributes,
            imageFileNames = savedImageFileNames
        )

        _pendingListings.value = _pendingListings.value + pending
        saveToDiskLocked()
        return@withLock localId
    }

    private const val MAX_SERVER_REJECTIONS = 3

    private fun replaceItemLocked(updated: PendingOfflineListing) {
        _pendingListings.value = _pendingListings.value.map { if (it.localId == updated.localId) updated else it }
        saveToDiskLocked()
    }

    private fun removeItemLocked(item: PendingOfflineListing) {
        val imagesDir = getImagesDir()
        item.imageFileNames.forEach { File(imagesDir, it).delete() }
        _pendingListings.value = _pendingListings.value.filterNot { it.localId == item.localId }
        saveToDiskLocked()
    }

    /**
     * رفع ومزامنة عناصر المستخدم الحالي فقط (لا نرفع أبداً إعلان مستخدم آخر بجلسة مختلفة).
     * كل خطوة (إنشاء الإعلان، رفع كل صورة) تُحفظ فور نجاحها، فلا يتكرر الإعلان ولا الصور عند إعادة المحاولة.
     * الأخطاء المؤقتة (الشبكة) تُعاد لاحقاً؛ ورفض الخادم المتكرر يُسقط العنصر مع رسالة للمستخدم.
     */
    suspend fun syncPendingItems(client: SupabaseClient): Int = mutex.withLock {
        val userId = client.auth.currentUserOrNull()?.id ?: return@withLock 0
        val items = _pendingListings.value.filter { it.ownerId == userId }
        if (items.isEmpty()) return@withLock 0

        _isSyncing.value = true
        _lastSyncMessage.value = "جاري مزامنة ورفع ${items.size} إعلان محفوظ أوفلاين..."

        var syncedCount = 0
        var droppedTitle: String? = null
        val imagesDir = getImagesDir()
        val listingRepo = ListingRepository(client)

        for (original in items) {
            var item = original
            val outcome = runCatching {
                // 1) إنشاء الإعلان مرة واحدة فقط (الحالة النهائية يحددها الخادم حسب مراجعة المنصة)
                if (item.createdListingId == null) {
                    val attrs = item.attributes.mapValues { (_, v) ->
                        JsonPrimitive(v) as kotlinx.serialization.json.JsonElement
                    }
                    val created = client.postgrest["listings"].insert(
                        Listing(
                            owner_id = item.ownerId,
                            platform_id = item.platformId,
                            category_id = item.categoryId,
                            title = item.title,
                            description = item.description,
                            price = item.price ?: 0.0,
                            currency = item.currency,
                            city = item.city,
                            contact_phone = item.contactPhone,
                            attributes = attrs,
                            status = "published"
                        )
                    ) { select() }.decodeSingle<Listing>()
                    item = item.copy(createdListingId = created.id ?: error("تعذر الحصول على معرف الإعلان المرفوع"))
                    replaceItemLocked(item)
                }
                val listingId = item.createdListingId!!

                // 2) رفع الصور واحدة واحدة مع حفظ التقدم
                for (fileName in item.imageFileNames.toList()) {
                    val file = File(imagesDir, fileName)
                    if (file.exists()) {
                        val url = listingRepo.uploadListingImage(listingId, file.readBytes())
                        file.delete()
                        item = item.copy(
                            imageFileNames = item.imageFileNames - fileName,
                            uploadedUrls = item.uploadedUrls + url
                        )
                    } else {
                        item = item.copy(imageFileNames = item.imageFileNames - fileName)
                    }
                    replaceItemLocked(item)
                }

                // 3) ربط الصور بالإعلان
                if (item.uploadedUrls.isNotEmpty()) {
                    val jsonAttrs = buildJsonObject {
                        item.attributes.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                        put("cover_image", JsonPrimitive(item.uploadedUrls.first()))
                        put("photos", JsonArray(item.uploadedUrls.map { JsonPrimitive(it) }))
                    }
                    client.postgrest["listings"].update(buildJsonObject { put("attributes", jsonAttrs) }) {
                        filter { eq("id", listingId) }
                    }
                }
            }

            if (outcome.isSuccess) {
                syncedCount++
                removeItemLocked(item)
            } else {
                val error = outcome.exceptionOrNull()
                if (isNetworkError(error)) {
                    replaceItemLocked(item)               // نحتفظ بالتقدم ونعيد لاحقاً
                } else {
                    val bumped = item.copy(failedAttempts = item.failedAttempts + 1)
                    if (bumped.failedAttempts >= MAX_SERVER_REJECTIONS) {
                        droppedTitle = bumped.title
                        removeItemLocked(bumped)
                    } else {
                        replaceItemLocked(bumped)
                    }
                }
            }
        }

        _isSyncing.value = false
        val remainingForUser = _pendingListings.value.count { it.ownerId == userId }
        _lastSyncMessage.value = when {
            droppedTitle != null -> "تعذّر رفع الإعلان «$droppedTitle» بعد عدة محاولات (رفضه الخادم). أعد إنشاءه بعد مراجعة بياناته."
            syncedCount > 0 -> "تمت مزامنة ورفع $syncedCount إعلان بنجاح فور توفر الإنترنت ✓"
            remainingForUser > 0 -> "فشلت المزامنة، سيتم المحاولة مجدداً تلقائياً"
            else -> null
        }

        return@withLock syncedCount
    }

    /** يحذف كل ما هو معلّق لمستخدم (يُستدعى عند حذف الحساب) مع صوره المحلية */
    suspend fun clearForOwner(ownerId: String) = mutex.withLock {
        val imagesDir = getImagesDir()
        _pendingListings.value.filter { it.ownerId == ownerId }
            .forEach { item -> item.imageFileNames.forEach { File(imagesDir, it).delete() } }
        _pendingListings.value = _pendingListings.value.filterNot { it.ownerId == ownerId }
        saveToDiskLocked()
    }

    fun removePending(localId: String) {
        scope.launch {
            mutex.withLock {
                val item = _pendingListings.value.find { it.localId == localId }
                if (item != null) {
                    val imagesDir = getImagesDir()
                    item.imageFileNames.forEach { File(imagesDir, it).delete() }
                    _pendingListings.value = _pendingListings.value.filterNot { it.localId == localId }
                    saveToDiskLocked()
                }
            }
        }
    }
}
