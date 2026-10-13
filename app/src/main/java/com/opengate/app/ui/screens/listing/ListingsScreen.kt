package com.opengate.app.ui.screens.listing

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.opengate.app.data.currency.CurrencyManager
import com.opengate.app.data.favorites.FavoritesStore
import com.opengate.app.data.model.Category
import com.opengate.app.data.settings.SettingsStore
import com.opengate.app.ui.components.parseImageModel
import com.opengate.app.ui.components.sanitizeErrorMessage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    viewModel: ListingsViewModel,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    val favIds by FavoritesStore.favoriteIds.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val filteredCards = remember(state.cards, searchQuery) {
        if (searchQuery.isBlank()) state.cards
        else state.cards.filter {
            it.listing.title.contains(searchQuery, ignoreCase = true) ||
                it.listing.city?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(state.platform?.name_ar ?: "الإعلانات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            val isOnline by com.opengate.app.data.offline.NetworkMonitor.isOnline.collectAsState()
            if (!isOnline) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "وضع عدم الاتصال: يتم عرض الإعلانات المحفوظة محلياً في الذاكرة",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في الإعلانات والمدن...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            // تصنيفات أفقية
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text("الكل") }
                    )
                }
                items(state.categories) { c: Category ->
                    FilterChip(
                        selected = state.selectedCategoryId == c.id,
                        onClick = { viewModel.onCategorySelected(c.id) },
                        label = { Text(c.name_ar) }
                    )
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(sanitizeErrorMessage(state.error), color = MaterialTheme.colorScheme.error)
                }
                filteredCards.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد إعلانات مطابقة حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.gridColumns),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCards) { card ->
                        val listingId = card.listing.id ?: ""
                        val isFav = favIds.contains(listingId)
                        ListingGridCard(
                            card = card,
                            isFav = isFav,
                            onToggleFav = { FavoritesStore.toggleFavorite(listingId) },
                            settings = settings,
                            onClick = { onOpenListing(listingId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListingGridCard(
    card: ListingCard,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    settings: com.opengate.app.data.settings.AppSettings,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                if (card.imageUrls.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { card.imageUrls.size })
                    LaunchedEffect(pagerState, card.imageUrls.size) {
                        if (card.imageUrls.size > 1) {
                            while (true) {
                                delay(3000)
                                val next = (pagerState.currentPage + 1) % card.imageUrls.size
                                pagerState.animateScrollToPage(next)
                            }
                        }
                    }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = parseImageModel(card.imageUrls[page]),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // طبقة تدرج لوني خفيفة
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )

                // زر المفضلة السريع
                IconButton(
                    onClick = onToggleFav,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "تفضيل",
                        tint = if (isFav) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // شارة المدينة
                card.listing.city?.let { rawCity ->
                    val cleanCity = rawCity.replace(Regex("https?://\\S+"), "").trim()
                    if (cleanCity.isNotBlank() && !cleanCity.contains("supabase") && !cleanCity.contains("listing-images")) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍 $cleanCity", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(10.dp)) {
                Text(
                    card.listing.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                card.listing.price?.let {
                    Text(
                        CurrencyManager.formatPrice(
                            it,
                            card.listing.currency,
                            settings.currency,
                            settings.exchangeRates
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // أزرار تواصل سريعة (اتصال / واتساب)
                card.listing.contact_phone?.let { phone ->
                    if (phone.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        runCatching {
                                            com.opengate.app.util.PhoneUtils.dialIntent(phone)
                                                ?.let { context.startActivity(it) }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(4.dp))
                                    Text("اتصال", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF22C55E).copy(alpha = 0.15f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        runCatching {
                                            com.opengate.app.util.PhoneUtils.whatsAppIntent(phone)
                                                ?.let { context.startActivity(it) }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("💬", style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.width(4.dp))
                                    Text("واتساب", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
