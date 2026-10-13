package com.opengate.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opengate.app.data.currency.AppCurrency
import com.opengate.app.data.currency.MarketRegion
import com.opengate.app.data.settings.SettingsStore
import com.opengate.app.data.settings.ThemeMode
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAccountDeleted: () -> Unit = onBack) {
    val settings by SettingsStore.settings.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            SectionLabel("مظهر التطبيق")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.DARK to "داكن",
                    ThemeMode.LIGHT to "فاتح",
                    ThemeMode.SYSTEM to "حسب الجهاز"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { SettingsStore.setTheme(mode) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("عدد أعمدة عرض الإعلانات")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2 to "عمودين", 3 to "3 أعمدة").forEach { (n, label) ->
                    FilterChip(
                        selected = settings.gridColumns == n,
                        onClick = { SettingsStore.setColumns(n) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("العملة المفضلة لعرض الأسعار")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCurrency.values().forEach { cur ->
                    FilterChip(
                        selected = settings.currency == cur,
                        onClick = { SettingsStore.setCurrency(cur) },
                        label = { Text("${cur.flag} ${cur.nameAr} (${cur.symbolAr})") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("سوق أسعار الصرف المعتمد")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MarketRegion.SANAA to "أسعار صنعاء", MarketRegion.ADEN to "أسعار عدن").forEach { (reg, label) ->
                    FilterChip(
                        selected = settings.marketRegion == reg,
                        onClick = { SettingsStore.setMarketRegion(reg) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("التنبيهات والإشعارات")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إشعارات الرسائل والمزايدات")
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { SettingsStore.setNotifications(it) }
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("الملف الشخصي وبيانات التواصل")
            val context = androidx.compose.ui.platform.LocalContext.current
            val client = remember { com.opengate.app.di.SupabaseProvider.client(context) }
            val currentUserId = remember { client.auth.currentUserOrNull()?.id }
            val currentUserEmail = remember { client.auth.currentUserOrNull()?.email }
            val scope = rememberCoroutineScope()

            var fullName by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            var accountType by remember { mutableStateOf("individual") }
            var businessName by remember { mutableStateOf("") }
            var savingProfile by remember { mutableStateOf(false) }
            var profileMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(currentUserId) {
                if (currentUserId != null) {
                    runCatching {
                        // my_profile: عرض خاص بصاحب الحساب (الهاتف ورقم الرخصة لا يظهران في profiles العامة)
                        client.postgrest["my_profile"].select()
                            .decodeSingleOrNull<com.opengate.app.data.model.Profile>()
                    }.onSuccess { p ->
                        if (p != null) {
                            fullName = p.full_name ?: ""
                            phone = p.phone ?: ""
                            accountType = p.account_type
                            businessName = p.business_name ?: ""
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("بيانات المستخدم والتاجر", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("الاسم الكامل") },
                        placeholder = { Text("مثال: علي أحمد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف للتواصل (مكالمات وواتساب)") },
                        placeholder = { Text("مثال: 771234567") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("نوع الحساب والنشاط:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "individual" to "👤 فرد",
                            "car_dealer" to "🚗 معرض سيارات",
                            "real_estate_office" to "🏡 مكتب عقاري",
                            "business" to "🏢 نشاط تجاري"
                        ).forEach { (typeKey, label) ->
                            FilterChip(
                                selected = accountType == typeKey,
                                onClick = { accountType = typeKey },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (accountType != "individual") {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("اسم المعرض / المكتب التجاري") },
                            placeholder = { Text("مثال: معرض النخبة للسيارات") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    profileMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (currentUserId == null) {
                                profileMessage = "يرجى تسجيل الدخول أولاً لحفظ البيانات"
                                return@Button
                            }
                            savingProfile = true
                            profileMessage = null
                            scope.launch {
                                runCatching {
                                    val phoneRaw = phone.trim()
                                    if (phoneRaw.isNotEmpty() && !com.opengate.app.util.PhoneUtils.isValid(phoneRaw)) {
                                        error("رقم الهاتف غير صالح (7 إلى 15 رقماً)")
                                    }
                                    // تحديث الحقول المسموحة فقط (لا نرسل is_verified ولا رقم الرخصة، فلا نمحو التوثيق عن غير قصد)
                                    val changed = client.postgrest["profiles"].update(
                                        buildJsonObject {
                                            put("full_name", fullName.trim().ifBlank { null })
                                            put("phone", if (phoneRaw.isEmpty()) null else com.opengate.app.util.PhoneUtils.normalize(phoneRaw))
                                            put("account_type", accountType)
                                            put("business_name", businessName.trim().ifBlank { null })
                                        }
                                    ) {
                                        select()
                                        filter { eq("id", currentUserId) }
                                    }.decodeList<kotlinx.serialization.json.JsonObject>()
                                    if (changed.isEmpty()) error("تعذّر حفظ البيانات، حاول تسجيل الدخول مجدداً")
                                }.onSuccess {
                                    savingProfile = false
                                    profileMessage = "تم حفظ وتحديث بيانات الملف الشخصي بنجاح ✓"
                                }.onFailure { ex ->
                                    savingProfile = false
                                    profileMessage = com.opengate.app.ui.components.sanitizeErrorMessage(ex)
                                }
                            }
                        },
                        enabled = !savingProfile,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (savingProfile) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("حفظ بيانات التواصل والنشاط", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("معلومات الحساب")

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "البريد الإلكتروني:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            currentUserEmail ?: "زائر (غير مسجل)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "حالة الحساب:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                if (currentUserId != null) "حساب نشط ✓" else "وضع الزائر",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "نوع النشاط:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val typeLabel = when (accountType) {
                            "car_dealer" -> "معرض سيارات"
                            "real_estate_office" -> "مكتب عقاري"
                            "business" -> "نشاط تجاري"
                            else -> "حساب شخصي"
                        }
                        Text(
                            typeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (currentUserId != null) {
                        Spacer(Modifier.height(14.dp))
                        var showDeleteAccountDialog by remember { mutableStateOf(false) }
                        var deletingAccount by remember { mutableStateOf(false) }
                        var deleteError by remember { mutableStateOf<String?>(null) }

                        OutlinedButton(
                            onClick = { showDeleteAccountDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🗑️ حذف الحساب والبيانات نهائياً (Google Play Policy)", style = MaterialTheme.typography.labelMedium)
                        }

                        if (showDeleteAccountDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteAccountDialog = false },
                                icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                title = { Text("تأكيد حذف الحساب والبيانات") },
                                text = {
                                    Text(
                                        (deleteError?.let { "$it\n\n" } ?: "") +
                                            "سيؤدي تأكيد الحذف إلى حذف حسابك نهائياً مع ملفك الشخصي وإعلاناتك ومفضلتك وتقييماتك ومحادثاتك ومزايداتك، ولا يمكن التراجع. هل ترغب بالاستمرار؟",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            deletingAccount = true
                                            deleteError = null
                                            scope.launch {
                                                // الحذف الفعلي يتم داخل القاعدة (دالة delete_my_account) وليس بحذف صف profiles فقط
                                                runCatching { client.postgrest.rpc("delete_my_account") }
                                                    .onSuccess {
                                                        if (currentUserId != null) {
                                                            runCatching { com.opengate.app.data.offline.OfflineSyncManager.clearForOwner(currentUserId) }
                                                        }
                                                        runCatching { client.auth.signOut() }
                                                        deletingAccount = false
                                                        showDeleteAccountDialog = false
                                                        onAccountDeleted()
                                                    }
                                                    .onFailure { ex ->
                                                        deletingAccount = false
                                                        deleteError = com.opengate.app.ui.components.sanitizeErrorMessage(ex)
                                                    }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        enabled = !deletingAccount
                                    ) {
                                        Text("نعم، حذف الحساب والبيانات")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteAccountDialog = false }) {
                                        Text("إلغاء")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("خدمات متجر Google Play")

            val currentActivity = (context as? android.app.Activity)
            var updateStatusMessage by remember { mutableStateOf<String?>(null) }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (currentActivity != null) {
                                    com.opengate.app.util.GooglePlayServicesHelper.launchInAppReview(currentActivity)
                                } else {
                                    com.opengate.app.util.GooglePlayServicesHelper.openPlayStorePage(context)
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("تقييم التطبيق على متجر Google Play", fontWeight = FontWeight.SemiBold)
                                Text("تقييمك يساعدنا على تحسين الخدمات للمستخدمين", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("❯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (currentActivity != null) {
                                    com.opengate.app.util.GooglePlayServicesHelper.checkForAppUpdate(currentActivity) { isAvailable ->
                                        updateStatusMessage = if (isAvailable) "يوجد تحديث متوفر في متجر Google Play!" else "تطبيقك محدث إلى آخر إصدار رسمي ✓"
                                    }
                                } else {
                                    updateStatusMessage = "تطبيقك محدث إلى آخر إصدار رسمي ✓"
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("فحص التحديثات في متجر Google Play", fontWeight = FontWeight.SemiBold)
                                Text("التحقق من توفر أحدث إصدار معتمد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("❯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    updateStatusMessage?.let { status ->
                        Spacer(Modifier.height(6.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("الامتثال والسياسات القانونية لـ Google Play")

            var showPrivacyDialog by remember { mutableStateOf(false) }
            var showTermsDialog by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔒", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Text("سياسة الخصوصية وأمان البيانات", fontWeight = FontWeight.SemiBold)
                        }
                        Text("❯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showTermsDialog = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📜", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Text("شروط الاستخدام وإرشادات المحتوى (UGC)", fontWeight = FontWeight.SemiBold)
                        }
                        Text("❯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (showPrivacyDialog) {
                AlertDialog(
                    onDismissRequest = { showPrivacyDialog = false },
                    title = { Text("🔒 سياسة الخصوصية وأمان البيانات") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "نحن في Open Gate نلتزم بحماية خصوصيتك وبياناتك وفق سياسات برنامج مطوري Google Play:\n\n" +
                                "1. البيانات المجمعة: يتم جمع البريد الإلكتروني ورقم الهاتف للتواصل فقط عند إدخالهما طواعية لنشر الإعلانات وتسهيل البيع والشراء.\n" +
                                "2. أذونات الجهاز: لا نطلب أي أذونات تخزين واسعة، ويتم اختيار الصور حصراً عبر أداة الصور الآمنة للنظام (Photo Picker) دون أي وصول لملفاتك الخاصة.\n" +
                                "3. عدم مشاركة البيانات: لا نبيع أو نشارك بياناتك مع أي طرف ثالث لأغراض إعلانية.\n" +
                                "4. حق حذف البيانات: يمكنك طلب حذف حسابك وبياناتك في أي وقت مباشرة من زر حذف الحساب في الإعدادات.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showPrivacyDialog = false }) {
                            Text("موافق")
                        }
                    }
                )
            }

            if (showTermsDialog) {
                AlertDialog(
                    onDismissRequest = { showTermsDialog = false },
                    title = { Text("📜 شروط الاستخدام وإرشادات المحتوى") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "وفق سياسة المحتوى المنشأ بواسطة المستخدمين (UGC) في Google Play:\n\n" +
                                "1. يحظر تماماً نشر أي سلع غير قانونية، أسلحة، مواد محظورة، أو سلع مقلدة.\n" +
                                "2. يحظر النشر الاحتيالي، التضليل، أو انتهاك حقوق الملكية الفكرية للآخرين.\n" +
                                "3. يحظر أي محتوى مسيء، تشهيري، أو يتضمن كراهية.\n" +
                                "4. يحق لإدارة Open Gate مراجعة وحذف أي إعلان مخالف وحظر حساب المستخدم المخالف فوراً.\n" +
                                "5. يتاح لكل مستخدم الإبلاغ عن أي إعلان أو معلن مخالف عبر زر الإبلاغ في صفحة الإعلان.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showTermsDialog = false }) {
                            Text("موافق")
                        }
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("OPEN GATE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text("منصة الإعلانات والمزادات المباشرة • الإصدار 1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp))
}
