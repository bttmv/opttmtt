package com.opengate.app.util

import android.content.Intent
import android.net.Uri

/** أدوات أرقام الهاتف: توحيد الأرقام العربية الهندية، وتجهيز روابط الاتصال والواتساب بأمان. */
object PhoneUtils {

    private const val DEFAULT_COUNTRY_CODE = "967" // اليمن — يُطبَّق فقط على الأرقام المحلية التي تبدأ بصفر

    /** يحوّل ٠-٩ و ۰-۹ إلى 0-9 */
    fun toAsciiDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            when (c) {
                in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
                else -> append(c)
            }
        }
    }

    /** يُبقي الأرقام وعلامة + في البداية فقط. */
    fun normalize(raw: String): String {
        val ascii = toAsciiDigits(raw.trim())
        val hasPlus = ascii.startsWith("+")
        val digits = ascii.filter { it in '0'..'9' }
        return if (hasPlus) "+$digits" else digits
    }

    /** صالح إذا كان 7–15 رقماً (مع + اختيارية). */
    fun isValid(raw: String): Boolean {
        val n = normalize(raw)
        val digitCount = n.count { it in '0'..'9' }
        return digitCount in 7..15
    }

    /** رقم بصيغة دولية بدون + لروابط wa.me، أو null إن لم يكن صالحاً. */
    fun toWhatsAppNumber(raw: String): String? {
        val n = normalize(raw)
        if (!isValid(n)) return null
        var digits = n.filter { it in '0'..'9' }
        digits = when {
            n.startsWith("+") -> digits
            digits.startsWith("00") -> digits.substring(2)
            digits.startsWith("0") -> DEFAULT_COUNTRY_CODE + digits.substring(1)
            else -> digits
        }
        return digits.takeIf { it.length in 7..15 }
    }

    fun dialIntent(raw: String): Intent? {
        val n = normalize(raw)
        if (!isValid(n)) return null
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", n, null))
    }

    fun whatsAppIntent(raw: String): Intent? {
        val number = toWhatsAppNumber(raw) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))
    }
}
