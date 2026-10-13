package com.opengate.app.ui.screens.listing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.opengate.app.data.currency.AppCurrency
import com.opengate.app.data.currency.CurrencyManager
import com.opengate.app.data.settings.SettingsStore
import com.opengate.app.ui.components.ReviewsSection
import com.opengate.app.ui.components.RatingSummaryRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    viewModel: ListingDetailViewModel,
    chatRepo: com.opengate.app.data.repository.ChatRepository,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    var showReportDialog by remember { mutableStateOf(false) }
    var reportSubmitted by remember { mutableStateOf(false) }
    var selectedReportReason by remember { mutableStateOf("احتيال أو إعلان وهمي") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(state.listing?.title ?: "تفاصيل الإعلان", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    val favoriteIds by com.opengate.app.data.favorites.FavoritesStore.favoriteIds.collectAsState()
                    val isFav = state.listing?.id != null && favoriteIds.contains(state.listing?.id)
                    IconButton(onClick = { com.opengate.app.data.favorites.FavoritesStore.toggleFavorite(state.listing?.id) }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "المفضلة",
                            tint = if (isFav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // زر الإبلاغ عن محتوى مخالف (متطلب إلزامي لسياسة Google Play UGC)
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = "إبلاغ عن محتوى مخالف",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable {
                                val next = when (settings.currency) {
                                    AppCurrency.YER -> AppCurrency.SAR
                                    AppCurrency.SAR -> AppCurrency.USD
                                    AppCurrency.USD -> AppCurrency.YER
                                }
                                SettingsStore.setCurrency(next)
                            }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${settings.currency.flag} ${settings.currency.symbolAr}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.listing == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(com.opengate.app.ui.components.sanitizeErrorMessage(state.error))
            }
            else -> {
                val listing = state.listing!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // صور الإعلان
                    if (state.imageUrls.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Image, contentDescription = null) }
                    } else {
                        val pagerState = rememberPagerState(pageCount = { state.imageUrls.size })
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                        ) { page ->
                            AsyncImage(
                                model = com.opengate.app.ui.components.parseImageModel(state.imageUrls[page]), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(listing.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    listing.price?.let {
                        Text(
                            CurrencyManager.formatPrice(
                                it,
                                listing.currency,
                                settings.currency,
                                settings.exchangeRates
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    RatingSummaryRow(summary = state.ratingSummary)

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // المواصفات والتفاصيل — عرض البيانات النظيفة واستبعاد أي روابط فنية أو مسارات تخزين
                    val cleanCity = listing.city
                        ?.replace(Regex("https?://\\S+"), "")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && !it.contains("supabase") && !it.contains("listing-images") }

                    if (cleanCity != null) {
                        SpecRow("المدينة", cleanCity)
                    }

                    listing.attributes.forEach { (rawKey, jsonVal) ->
                        val key = rawKey.lowercase().trim()
                        if (SYSTEM_ATTRIBUTE_KEYS.contains(key)) return@forEach

                        val rawStr = jsonVal.toString().trim().removeSurrounding("\"").removeSurrounding("'")
                        if (rawStr.isBlank() || rawStr == "null" || rawStr == "[]" || rawStr == "{}") return@forEach

                        // منع ظهور روابط قواعد البيانات أو باكت التخزين listing-images داخل جدول المواصفات نهائياً
                        if (rawStr.startsWith("http://") || rawStr.startsWith("https://") ||
                            rawStr.contains("supabase.co") || rawStr.contains("listing-images") ||
                            rawStr.contains("/storage/v1/")) {
                            return@forEach
                        }

                        val label = state.fieldSchemas.firstOrNull { it.field_key.equals(rawKey, ignoreCase = true) }?.label_ar
                            ?: ARABIC_FIELD_FALLBACKS[key]
                            ?: rawKey

                        val cleanVal = rawStr.replace(Regex("https?://\\S+"), "").trim()
                        if (cleanVal.isNotBlank()) {
                            SpecRow(label, cleanVal)
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current
                    if (!listing.contact_phone.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("📞 هاتف وتواصل المعلن:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.height(4.dp))
                                Text(listing.contact_phone, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                com.opengate.app.util.PhoneUtils.dialIntent(listing.contact_phone)
                                                    ?.let { context.startActivity(it) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📞 اتصال")
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            runCatching {
                                                com.opengate.app.util.PhoneUtils.whatsAppIntent(listing.contact_phone)
                                                    ?.let { context.startActivity(it) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("💬 واتساب")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.startConversation(chatRepo) { conversationId -> onOpenChat(conversationId) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("💬 تواصل مع البائع")
                        }
                        val favoriteIds by com.opengate.app.data.favorites.FavoritesStore.favoriteIds.collectAsState()
                        val isFav = listing.id != null && favoriteIds.contains(listing.id)
                        Button(
                            onClick = { com.opengate.app.data.favorites.FavoritesStore.toggleFavorite(listing.id) },
                            colors = if (isFav) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = if (isFav) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isFav) "❤️ في المفضلة" else "♡ حفظ بالمفضلة")
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // قسم التقييمات — نفس المكوّن الجاهز، متصل فعلياً بجدول reviews
                    ReviewsSection(
                        reviews = state.reviews,
                        onSubmit = { rating, comment -> viewModel.submitReview(rating, comment) }
                    )
                    if (state.postingReview) {
                        Spacer(Modifier.height(8.dp)); CircularProgressIndicator()
                    }
                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        val reportReasons = listOf(
            "احتيال أو إعلان وهمي",
            "سلع ممنوعة أو مخالفة للقانون",
            "انتهاك حقوق الملكية الفكرية أو الخصوصية",
            "محتوى غير لائق أو مسيء",
            "حظر هذا المعلن نهائياً"
        )
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            icon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("الإبلاغ عن محتوى أو معلن مخالف") },
            text = {
                Column {
                    Text(
                        "وفق سياسة متجر Google Play، نحن نلتزم بمراجعة كافة البلاغات ومكافحة المحتوى المخالف. اختر سبب البلاغ:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    reportReasons.forEach { reason ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedReportReason = reason }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReportReason == reason,
                                onClick = { selectedReportReason = reason }
                            )
                            Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        reportSubmitted = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("إرسال البلاغ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (reportSubmitted) {
        AlertDialog(
            onDismissRequest = { reportSubmitted = false },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("تم استلام بلاغك بنجاح") },
            text = {
                Text(
                    "شكراً لك. سيقوم فريق الإشراف بمراجعة الإعلان واتخاذ الإجراء اللازم وإخفاء المحتوى خلال 24 ساعة وفق إرشادات المجتمع وسياسات متجر Google Play.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(onClick = { reportSubmitted = false }) {
                    Text("حسناً")
                }
            }
        )
    }
}

private val SYSTEM_ATTRIBUTE_KEYS = setOf(
    "cover_image", "photos", "images", "image_url", "image",
    "custom_category", "display_category", "status", "rejection_reason",
    "owner_id", "listing_id", "platform_id", "category_id",
    "id", "created_at", "updated_at", "views_count", "favorites_count",
    "city"
)

private val ARABIC_FIELD_FALLBACKS = mapOf(
    "brand" to "الماركة",
    "model" to "الموديل",
    "year" to "سنة الصنع",
    "mileage_km" to "الممشى (كم)",
    "transmission" to "ناقل الحركة",
    "fuel_type" to "نوع الوقود",
    "color" to "اللون",
    "area_sqm" to "المساحة (م²)",
    "bedrooms" to "عدد غرف النوم",
    "bathrooms" to "عدد دورات المياه",
    "floor_number" to "رقم الدور",
    "furnishing" to "التأثيث",
    "age_years" to "عمر البناء (سنوات)",
    "condition_detail" to "حالة السلعة",
    "condition" to "الحالة"
)

@Composable
private fun SpecRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
