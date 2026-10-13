package com.opengate.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.opengate.app.BuildConfig
import com.opengate.app.data.currency.AppCurrency
import com.opengate.app.data.currency.ExchangeRates
import com.opengate.app.data.currency.MarketRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,   // الافتراضي: الثيم الداكن الفاخر
    val gridColumns: Int = 2,                    // 2 أو 3
    val notificationsEnabled: Boolean = true,
    val currency: AppCurrency = AppCurrency.YER,  // العملة الافتراضية: الريال اليمني
    val marketRegion: MarketRegion = MarketRegion.SANAA, // صنعاء / عدن
    val exchangeRates: ExchangeRates = ExchangeRates()
)

/**
 * مخزن إعدادات وتفضيلات المستخدم — يحفظ اختياراته على الجهاز (الثيم، عدد الأعمدة، الإشعارات، العملة المختارة).
 */
object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("open_gate_settings", Context.MODE_PRIVATE)

        val savedCurrencyCode = prefs.getString("selected_currency", AppCurrency.YER.code)
        val savedCurrency = AppCurrency.fromCode(savedCurrencyCode)

        val savedRegionName = prefs.getString("market_region", MarketRegion.SANAA.name)
        val savedRegion = runCatching { MarketRegion.valueOf(savedRegionName!!) }.getOrDefault(MarketRegion.SANAA)

        _settings.value = AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.DARK.name)!!) }
                .getOrDefault(ThemeMode.DARK),
            gridColumns = prefs.getInt("columns", 2).coerceIn(2, 3),
            notificationsEnabled = prefs.getBoolean("notifications", true),
            currency = savedCurrency,
            marketRegion = savedRegion,
            exchangeRates = ExchangeRates(activeRegion = savedRegion)
        )
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setColumns(n: Int) {
        prefs.edit().putInt("columns", n).apply()
        _settings.value = _settings.value.copy(gridColumns = n)
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("notifications", enabled).apply()
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }

    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString("selected_currency", currency.code).apply()
        _settings.value = _settings.value.copy(currency = currency)
    }

    fun setMarketRegion(region: MarketRegion) {
        prefs.edit().putString("market_region", region.name).apply()
        val newRates = _settings.value.exchangeRates.copy(activeRegion = region)
        _settings.value = _settings.value.copy(marketRegion = region, exchangeRates = newRates)
    }

    fun getEffectiveSupabaseUrl(): String = BuildConfig.SUPABASE_URL

    fun getEffectiveSupabaseKey(): String = BuildConfig.SUPABASE_ANON_KEY

    fun isSupabaseConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() &&
        BuildConfig.SUPABASE_URL.startsWith("http") &&
        !BuildConfig.SUPABASE_URL.contains("YOUR_PROJECT", ignoreCase = true)
}
