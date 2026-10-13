package com.opengate.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opengate.app.data.model.Platform
import com.opengate.app.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPlatform: (Platform) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateListing: () -> Unit,
    onLogout: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMyListings: () -> Unit = {},
    onOpenAuctions: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.load()
    }
    val sponsorItems = remember(state.sponsors) { state.sponsors.map { it.text_ar } }
    val isAuctionsVisible = remember(state.platforms) {
        state.platforms.any { (it.key == "auctions" || it.is_auction_platform) && it.is_visible }
    }

    // مراقبة حالة الأوفلاين والمزامنة التلقائية
    val isOnline by com.opengate.app.data.offline.NetworkMonitor.isOnline.collectAsState()
    val pendingListings by com.opengate.app.data.offline.OfflineSyncManager.pendingListings.collectAsState()
    val isSyncing by com.opengate.app.data.offline.OfflineSyncManager.isSyncing.collectAsState()
    val lastSyncMessage by com.opengate.app.data.offline.OfflineSyncManager.lastSyncMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val client = remember { com.opengate.app.di.SupabaseProvider.client(context) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            "OPEN GATE",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "بوابتك المفتوحة للتجارة والمزادات الحرة",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("الرئيسية", fontWeight = FontWeight.SemiBold) },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } }
                )
                if (isAuctionsVisible) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Gavel, contentDescription = null, tint = Color(0xFFEF4444)) },
                        label = { Text("المزادات المباشرة 🔴", fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenAuctions()
                        }
                    )
                }
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE11D48)) },
                    label = { Text("❤️ المفضلة") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenFavorites()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("📋 إعلاناتي") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenMyListings()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null) },
                    label = { Text("💬 محادثاتي") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenChats()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("⚙️ الإعدادات والملف الشخصي") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (state.isAdmin) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = Color(0xFFEAB308)) },
                        label = { Text("👑 لوحة المالك / الإدارة", fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenAdmin()
                        }
                    )
                }
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                    label = { Text("🚪 تسجيل الخروج") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("OPEN GATE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "اليمن 🇾🇪",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "القائمة")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenFavorites) {
                            Icon(Icons.Filled.Favorite, contentDescription = "المفضلة", tint = Color(0xFFEF4444))
                        }
                        IconButton(onClick = onOpenChats) {
                            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "المحادثات")
                        }
                    }
                )
            },
            bottomBar = {
                ModernBottomBar(
                    onHomeClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onAuctionsClick = onOpenAuctions,
                    onCreateClick = onCreateListing,
                    onChatsClick = onOpenChats,
                    onFavoritesClick = onOpenFavorites
                )
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 0) شريط التنبيه بحالة عدم الاتصال والمزامنة التلقائية
                if (!isOnline || pendingListings.isNotEmpty() || lastSyncMessage != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!isOnline) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                                else if (pendingListings.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(if (!isOnline) "⚡" else "🔄", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                if (!isOnline) "وضع عدم الاتصال (أوفلاين)"
                                                else if (pendingListings.isNotEmpty()) "طابور المزامنة التلقائية"
                                                else "حالة المزامنة",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                if (!isOnline) "التصفح يعمل من الذاكرة المحلية، وأي إعلانات تضيفها ستُحفظ وتُرفع تلقائياً فور توفر الإنترنت"
                                                else if (pendingListings.isNotEmpty()) "يوجد ${pendingListings.size} إعلان محفوظ محلياً بانتظار الرفع إلى السيرفر"
                                                else lastSyncMessage ?: "",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    if (pendingListings.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    com.opengate.app.data.offline.OfflineSyncManager.syncPendingItems(client)
                                                }
                                            },
                                            enabled = !isSyncing && isOnline,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            if (isSyncing) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text("مزامنة الآن", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 1) شريط النبض الحي المتحرك (Marquee & Live Visitors Ticker)
                item {
                    MarketPulseTicker(
                        extraSponsors = sponsorItems,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2) شريط القصص والمعارض المميزة (Stories Row)
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "المعارض والقصص المميزة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "تحديث حي ⚡",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        TopDealersStoriesRow(
                            onOpenAuctions = onOpenAuctions,
                            onCreateListing = onCreateListing
                        )
                    }
                }

                // 3) سلايدر البانرات الفاخر التفاعلي (Hero Banner Carousel)
                item {
                    HeroBannerCarousel(
                        onOpenAuctions = onOpenAuctions,
                        onCreateListing = onCreateListing,
                        onBrowseMarket = {
                            scope.launch { listState.animateScrollToItem(4) }
                        }
                    )
                }

                // 4) شريط أسعار الصرف الحية (صنعاء وعدن)
                item {
                    CurrencyExchangeBar()
                }

                // 5) شريط إحصائيات الثقة وحجم السوق الحي
                item {
                    MarketStatsRow()
                }

                // 6) عنوان قسم المنصات
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "منصات وأقسام السوق",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${state.platforms.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        TextButton(onClick = onCreateListing) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("أضف إعلانك", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 7) بطاقات المنصات بتصميم فاخر وجذاب
                when {
                    state.loading -> {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    state.error != null -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    sanitizeErrorMessage(state.error),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        val platforms = state.platforms
                        val chunked = platforms.chunked(2)
                        items(chunked.size) { rowIndex ->
                            val pair = chunked[rowIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { platform ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        EnhancedPlatformCard(
                                            platform = platform,
                                            onClick = { onOpenPlatform(platform) }
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 8) روابط التواصل والشبكات الاجتماعية
                if (state.socialLinks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            state.socialLinks.forEach { link ->
                                AssistChip(
                                    onClick = {
                                        val v = link.value
                                        runCatching { uriHandler.openUri(if (v.startsWith("http")) v else "https://$v") }
                                    },
                                    label = { Text(link.label_ar) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedPlatformCard(
    platform: Platform,
    onClick: () -> Unit
) {
    val isAuctions = platform.key == "auctions"
    val isCars = platform.key == "cars" || platform.name_ar.contains("سيارات")
    val isRealEstate = platform.key == "real_estate" || platform.name_ar.contains("عقار")

    val bgGradient = when {
        isAuctions -> listOf(Color(0xFFFEF2F2), Color(0xFFFEE2E2))
        isCars -> listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE))
        isRealEstate -> listOf(Color(0xFFF0FDF4), Color(0xFFDCFCE7))
        else -> listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isAuctions) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(bgGradient))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // الأيقونة في دائرة عصرية مع ظل خفيف
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(62.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            platform.icon ?: "🏷️",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    platform.name_ar,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    when {
                        isAuctions -> "مزادات حية بالثواني"
                        isCars -> "معارض وسيارات فحص"
                        isRealEstate -> "فلل وأراضي وشقق"
                        else -> "تصفح العروض والأسعار"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAuctions) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // شارة المزاد المباشر في الزاوية
            if (isAuctions) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEF4444),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        "🔴 مباشر",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
