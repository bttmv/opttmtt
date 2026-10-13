package com.opengate.app.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.opengate.app.data.currency.CurrencyManager
import com.opengate.app.data.favorites.FavoritesStore
import com.opengate.app.data.model.Listing
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.data.settings.SettingsStore
import com.opengate.app.ui.components.parseImageModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    listingRepo: ListingRepository,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val favoriteIds by FavoritesStore.favoriteIds.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    var listings by remember { mutableStateOf<List<Pair<Listing, List<String>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoriteIds) {
        loading = true
        val list = mutableListOf<Pair<Listing, List<String>>>()
        favoriteIds.forEach { id ->
            runCatching {
                val l = listingRepo.getListingById(id)
                var images = listingRepo.getListingImages(id).map { it.url }
                if (images.isEmpty()) {
                    val cover = (l.attributes["cover_image"] as? JsonPrimitive)?.content
                    val photos = (l.attributes["photos"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                    images = if (!photos.isNullOrEmpty()) photos else if (cover != null) listOf(cover) else emptyList()
                }
                list.add(l to images)
            }
        }
        listings = list
        loading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("إعلاناتي المفضلة (${favoriteIds.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                loading && listings.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                listings.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "لا توجد إعلانات في المفضلة بعد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "انقر على زر القلب أو 'إضافة للمفضلة' في أي إعلان لحفظه هنا والوصول إليه بسرعة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onBack) {
                            Text("تصفح الإعلانات")
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(settings.gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(listings, key = { it.first.id ?: "" }) { (listing, images) ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenListing(listing.id ?: "") }
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                                        if (images.isNotEmpty()) {
                                            AsyncImage(
                                                model = parseImageModel(images.first()),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Image, contentDescription = null)
                                            }
                                        }

                                        // زر إزالة من المفضلة
                                        IconButton(
                                            onClick = { FavoritesStore.toggleFavorite(listing.id) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                        ) {
                                            Icon(
                                                Icons.Filled.Favorite,
                                                contentDescription = "حذف من المفضلة",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column(Modifier.padding(10.dp)) {
                                        Text(listing.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        listing.price?.let { p ->
                                            Text(
                                                CurrencyManager.formatPrice(
                                                    p,
                                                    listing.currency,
                                                    settings.currency,
                                                    settings.exchangeRates
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        listing.city?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
