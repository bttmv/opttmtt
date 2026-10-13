package com.opengate.app.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * مراقب حالة اتصال الإنترنت اللحظي:
 * يستمع لتغيرات الشبكة عبر ConnectivityManager.NetworkCallback ويعطي إشعاراً فورياً عند عودة الإنترنت.
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var onNetworkRestoredCallback: (() -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false

    fun init(context: Context, onNetworkRestored: (() -> Unit)? = null) {
        if (isInitialized) return
        isInitialized = true
        onNetworkRestoredCallback = onNetworkRestored

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            _isOnline.value = true
            return
        }

        // الفحص المبدئي
        _isOnline.value = checkInitialConnectivity(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_isOnline.value
                _isOnline.value = true
                if (wasOffline) {
                    scope.launch {
                        onNetworkRestoredCallback?.invoke()
                    }
                }
            }

            override fun onLost(network: Network) {
                // التأكد من عدم وجود شبكة أخرى نشطة قبل اعتبار الجهاز غير متصل
                _isOnline.value = checkInitialConnectivity(cm)
            }

            override fun onUnavailable() {
                _isOnline.value = false
            }
        })
    }

    private fun checkInitialConnectivity(cm: ConnectivityManager): Boolean {
        return runCatching {
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }.getOrDefault(true)
    }

    fun setOnNetworkRestoredListener(listener: () -> Unit) {
        onNetworkRestoredCallback = listener
    }
}
