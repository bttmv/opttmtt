package com.opengate.app.ui.screens.admin

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.opengate.app.data.model.Listing
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun listingCoverUrl(l: Listing): String? =
    (l.attributes["cover_image"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }

internal fun listingPriceText(l: Listing): String? =
    l.price?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%,.0f", it) + " " + l.currency }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationScreen(
    viewModel: ModerationViewModel,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var rejecting by remember { mutableStateOf<Listing?>(null) }
    var reason by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("مراجعة الإعلانات (${state.items.size})") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            if (state.items.isEmpty() && state.error == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد إعلانات بانتظار المراجعة ✓", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(state.items, key = { it.id ?: it.title }) { l ->
                val busy = state.busyId == l.id
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listingCoverUrl(l)?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Text(l.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        listingPriceText(l)?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                        Text(
                            listOfNotNull(l.city, l.contact_phone).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        l.description?.let {
                            Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { l.id?.let(onOpenListing) }) { Text("عرض التفاصيل الكاملة") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.approve(l) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (busy) "..." else "✓ اعتماد") }
                            OutlinedButton(
                                onClick = { reason = ""; rejecting = l },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("✕ رفض") }
                        }
                    }
                }
            }
        }
    }

    rejecting?.let { target ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("سبب رفض الإعلان") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 300) reason = it },
                    label = { Text("اكتب السبب (يراه صاحب الإعلان)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.reject(target, reason); rejecting = null },
                    enabled = reason.isNotBlank()
                ) { Text("تأكيد الرفض") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("إلغاء") } }
        )
    }
}
