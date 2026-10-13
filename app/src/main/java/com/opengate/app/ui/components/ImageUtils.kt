package com.opengate.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * يحلل رابط الصورة: إذا كانت رابط HTTP يمرره كما هو لـ Coil،
 * وإذا كانت بصيغة Data URI (data:image/jpeg;base64,...) يفك تشفيرها إلى Bitmap
 * لتعرضها مكتبة Coil و Jetpack Compose فوراً على الشاشة بدون أي تأخير أو مشاكل بالشبكة.
 */
fun parseImageModel(urlOrData: String): Any {
    if (urlOrData.isBlank()) return ""
    return if (urlOrData.startsWith("data:image/") && urlOrData.contains(";base64,")) {
        runCatching {
            val base64Part = urlOrData.substringAfter(";base64,")
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: urlOrData
        }.getOrDefault(urlOrData)
    } else {
        urlOrData
    }
}
