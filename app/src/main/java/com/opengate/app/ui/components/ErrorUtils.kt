package com.opengate.app.ui.components

private val ARABIC_RE = Regex("[\\u0600-\\u06FF]")
private val JSON_MESSAGE_RE = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

/**
 * يستخرج رسالة الخطأ العربية التي كتبناها نحن داخل القاعدة (raise exception ...) إن وُجدت،
 * ليظهر للمستخدم السبب الحقيقي (مثل: "المزايدة يجب أن تكون 550 على الأقل") بدل رسالة عامة.
 * لا يمرّر أي نص إنجليزي/تقني.
 */
private fun extractDbArabicMessage(raw: String): String? {
    JSON_MESSAGE_RE.find(raw)?.groupValues?.getOrNull(1)?.let { m ->
        val clean = m.replace("\\\"", "\"").replace("\\n", " ").trim()
        if (ARABIC_RE.containsMatchIn(clean) && clean.length <= 200) return clean
    }
    // بعض إصدارات المكتبة تضع النص مباشرة في سطر مستقل
    raw.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it.length <= 200 && ARABIC_RE.containsMatchIn(it) && !it.contains("http") }
        ?.let { return it }
    return null
}

/**
 * دالة مركزية لتنقية رسائل الخطأ:
 * تمنع ظهور أي مسميات تقنية (Supabase / PostgreSQL / أسماء الجداول / عناوين URL)،
 * وتُبقي فقط رسائل القاعدة العربية الآمنة المقصودة للمستخدم.
 */
fun sanitizeErrorMessage(error: Throwable?): String {
    if (error == null) return "حدث خطأ غير متوقع. يرجى المحاولة لاحقاً."
    return sanitizeErrorMessage(error.message)
}

fun sanitizeErrorMessage(message: String?): String {
    if (message.isNullOrBlank()) return "حدث خطأ أثناء معالجة الطلب. يرجى المحاولة لاحقاً."

    extractDbArabicMessage(message)?.let { return it }

    val lower = message.lowercase()

    // 1. أخطاء انقطاع الشبكة أو الخادم
    if (isNetworkErrorMessage(lower)) {
        return "تعذّر الاتصال بالإنترنت. يرجى التحقق من اتصال شبكتك والمحاولة مجدداً."
    }

    // 2. أخطاء انتهاء الجلسة
    if (lower.contains("401") || lower.contains("jwt") || lower.contains("unauthorized") ||
        lower.contains("not authenticated") || lower.contains("session_not_found") ||
        lower.contains("refresh_token")
    ) {
        return "انتهت صلاحية الجلسة، يرجى تسجيل الدخول مجدداً للمتابعة."
    }

    // 3. أخطاء الصلاحيات
    if (lower.contains("403") || lower.contains("permission denied") ||
        lower.contains("row-level security") || lower.contains("row level security") ||
        lower.contains("violates row-level")
    ) {
        return "ليست لديك صلاحية لتنفيذ هذه العملية."
    }

    // 4. أخطاء الحقول المكررة
    if (lower.contains("duplicate key") || lower.contains("unique constraint") || lower.contains("already exists")) {
        return "هذه البيانات مسجلة مسبقاً، يرجى استخدام بيانات أخرى."
    }

    // 5. ارتباط ببيانات غير موجودة أو مخالفة قيد
    if (lower.contains("foreign key") || lower.contains("violates check constraint") || lower.contains("violates")) {
        return "البيانات المدخلة غير صالحة. يرجى مراجعتها والمحاولة مجدداً."
    }

    // 6. حجب أي رسالة تحتوي على مسميات البنية التحتية والتقنية
    if (lower.contains("supabase") || lower.contains("postgrest") || lower.contains("gotrue") ||
        lower.contains("http") || lower.contains(".co") || lower.contains(".com") ||
        lower.contains("postgres") || lower.contains("table") || lower.contains("schema") ||
        lower.contains("sql") || lower.contains("bucket") || lower.contains("storage") ||
        lower.contains("rls") || lower.contains("policy")
    ) {
        return "تعذّر تحميل البيانات حالياً. يرجى إعادة المحاولة بعد قليل."
    }

    return "حدث خطأ أثناء تنفيذ العملية، يرجى المحاولة لاحقاً."
}

/** هل الخطأ ناتج عن انقطاع الشبكة (وليس رفضاً من الخادم)؟ يُستخدم لقرار الحفظ أوفلاين. */
fun isNetworkError(error: Throwable?): Boolean {
    var t: Throwable? = error
    var depth = 0
    while (t != null && depth < 6) {
        if (t is java.io.IOException) return true
        if (isNetworkErrorMessage(t.message?.lowercase().orEmpty())) return true
        t = t.cause
        depth++
    }
    return false
}

private fun isNetworkErrorMessage(lower: String): Boolean =
    lower.contains("unable to resolve host") ||
        lower.contains("connectexception") ||
        lower.contains("unknownhostexception") ||
        lower.contains("no address associated") ||
        lower.contains("failed to connect") ||
        lower.contains("sockettimeout") ||
        lower.contains("timeout") ||
        lower.contains("route to host") ||
        lower.contains("network is unreachable") ||
        lower.contains("connection reset") ||
        lower.contains("software caused connection abort")
