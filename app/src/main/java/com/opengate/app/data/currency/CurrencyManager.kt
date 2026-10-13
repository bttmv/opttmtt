package com.opengate.app.data.currency

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class AppCurrency(
    val code: String,
    val symbolAr: String,
    val nameAr: String,
    val flag: String
) {
    YER("YER", "ر.ي", "ريال يمني", "🇾🇪"),
    USD("USD", "$", "دولار أمريكي", "💵"),
    SAR("SAR", "ر.س", "ريال سعودي", "🇸🇦");

    companion object {
        fun fromCode(code: String?): AppCurrency {
            return when (code?.uppercase()) {
                "USD", "$" -> USD
                "SAR", "ر.س" -> SAR
                else -> YER
            }
        }
    }
}

enum class MarketRegion(val labelAr: String) {
    SANAA("أسعار صنعاء"),
    ADEN("أسعار عدن")
}

data class ExchangeRates(
    val usdToYerSanaa: Double = 535.0,
    val sarToYerSanaa: Double = 140.5,
    val usdToYerAden: Double = 1685.0,
    val sarToYerAden: Double = 442.0,
    val activeRegion: MarketRegion = MarketRegion.SANAA
) {
    fun getUsdRate(): Double = if (activeRegion == MarketRegion.SANAA) usdToYerSanaa else usdToYerAden
    fun getSarRate(): Double = if (activeRegion == MarketRegion.SANAA) sarToYerSanaa else sarToYerAden
}

object CurrencyManager {

    private val numberFormatter = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val decimalFormatter = DecimalFormat("#,##0.0", DecimalFormatSymbols(Locale.US))

    fun formatNumber(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            numberFormatter.format(amount)
        } else {
            decimalFormatter.format(amount)
        }
    }

    /**
     * تحويل المبلغ بين العملات حسب أسعار السوق المعتمدة
     */
    fun convert(
        amount: Double,
        from: AppCurrency,
        to: AppCurrency,
        rates: ExchangeRates = ExchangeRates()
    ): Double {
        if (from == to) return amount

        // تحويل أولاً إلى الريال اليمني كعملة وسيطة
        val amountInYer = when (from) {
            AppCurrency.YER -> amount
            AppCurrency.USD -> amount * rates.getUsdRate()
            AppCurrency.SAR -> amount * rates.getSarRate()
        }

        // تحويل من الريال اليمني إلى العملة المطلوبة
        return when (to) {
            AppCurrency.YER -> amountInYer
            AppCurrency.USD -> amountInYer / rates.getUsdRate()
            AppCurrency.SAR -> amountInYer / rates.getSarRate()
        }
    }

    /**
     * يعرض السعر بصيغة احترافية مع التحويل التلقائي إذا كانت العملة المفعلة مختلفة
     */
    fun formatPrice(
        amount: Double?,
        listingCurrencyCode: String = "YER",
        displayCurrency: AppCurrency = AppCurrency.YER,
        rates: ExchangeRates = ExchangeRates()
    ): String {
        if (amount == null) return "غير محدد"

        val originalCurrency = AppCurrency.fromCode(listingCurrencyCode)

        if (originalCurrency == displayCurrency) {
            return "${formatNumber(amount)} ${originalCurrency.symbolAr}"
        }

        // عند اختلاف العملة، نعرض السعر المحوّل مع السعر الأصلي للتوضيح
        val converted = convert(amount, originalCurrency, displayCurrency, rates)
        return "${formatNumber(converted)} ${displayCurrency.symbolAr} (${formatNumber(amount)} ${originalCurrency.symbolAr})"
    }
}
