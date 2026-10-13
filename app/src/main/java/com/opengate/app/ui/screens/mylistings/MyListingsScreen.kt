package com.opengate.app.ui.screens.mylistings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opengate.app.data.model.Listing

private fun statusLabel(status: String): Pair<String, Color> = when (status) {
    "published" -> "منشور" to Color(0xFF15803D)
    "pending_review" -> "قيد المراجعة" to Color(0xFFB45309)
    "rejected" -> "مرفوض" to Color(0xFFB91C1C)
    "sold" -> "تم البيع" to Color(0xFF0369A1)
    "expired" -> "منتهي" to Color(0xFF6B7280)
    "archived" -> "مؤرشف" to Color(0xFF6B7280)
    else -> "مسودة" to Color(0xFF6B7280)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    viewModel: MyListingsViewModel,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("إعلاناتي (${state.items.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = { IconButton(onClick = viewModel::load) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث") } }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            if (state.items.isEmpty() && state.error == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لم تنشر أي إعلان بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(state.items, key = { it.id ?: it.title }) { l ->
                MyListingCard(
                    listing = l,
                    busy = state.busyId == l.id,
                    onOpen = { l.id?.let(onOpenListing) },
                    onSold = { viewModel.markSold(l) },
                    onArchive = { viewModel.archive(l) }
                )
            }
        }
    }
}

@Composable
private fun MyListingCard(
    listing: Listing,
    busy: Boolean,
    onOpen: () -> Unit,
    onSold: () -> Unit,
    onArchive: () -> Unit
) {
    val (label, color) = statusLabel(listing.status)
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(listing.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(label, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            if (listing.price != null && listing.price > 0) {
                Text(
                    String.format(java.util.Locale.US, "%,.0f", listing.price) + " " + listing.currency,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (listing.status == "pending_review") {
                Text(
                    "إعلانك قيد مراجعة الإدارة وسيظهر للجميع بعد اعتماده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (listing.status == "rejected") {
                Text(
                    "سبب الرفض: " + (listing.rejection_reason ?: "لم يُذكر سبب"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (listing.status != "archived") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (listing.status == "published") {
                        OutlinedButton(onClick = onSold, enabled = !busy) { Text(if (busy) "..." else "تم البيع") }
                    }
                    OutlinedButton(onClick = onArchive, enabled = !busy) { Text("أرشفة") }
                }
            }
        }
    }
}
