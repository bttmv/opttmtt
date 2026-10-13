package com.opengate.app.data.offline

import android.content.Context
import com.opengate.app.data.model.Listing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * مدير التخزين المؤقت أوفلاين لقوائم الإعلانات:
 * يتيح تصفح إعلانات المنصات حتى عند انقطاع الإنترنت بالكامل.
 */
object OfflineCacheManager {

    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getCacheFile(platformId: String): File {
        val dir = File(appContext.filesDir, "cached_listings")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "platform_${platformId}.json")
    }

    fun cacheListings(platformId: String, listings: List<Listing>) {
        if (listings.isEmpty()) return
        scope.launch {
            runCatching {
                val file = getCacheFile(platformId)
                val content = json.encodeToString(listings)
                file.writeText(content)
            }
        }
    }

    fun getCachedListings(platformId: String): List<Listing> {
        return runCatching {
            val file = getCacheFile(platformId)
            if (file.exists()) {
                val content = file.readText()
                json.decodeFromString<List<Listing>>(content)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
    }
}
