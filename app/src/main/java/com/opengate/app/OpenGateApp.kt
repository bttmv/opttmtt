package com.opengate.app

import android.app.Application
import kotlinx.coroutines.launch
import com.opengate.app.data.settings.SettingsStore
import com.opengate.app.di.SupabaseProvider

class OpenGateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // يهيّئ عميل Supabase مرة واحدة عند إقلاع التطبيق
        SettingsStore.init(this)
        val client = SupabaseProvider.client(this)
        com.opengate.app.data.favorites.FavoritesStore.init(this, client)
        com.opengate.app.data.platforms.PlatformsStore.init(this, client)
        com.opengate.app.data.offline.OfflineSyncManager.init(this)
        com.opengate.app.data.offline.OfflineCacheManager.init(this)
        com.opengate.app.data.offline.NetworkMonitor.init(this) {
            // المزامنة والرفع الفوري التلقائي فور عودة الإنترنت
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.opengate.app.data.offline.OfflineSyncManager.syncPendingItems(client)
            }
        }
    }
}
