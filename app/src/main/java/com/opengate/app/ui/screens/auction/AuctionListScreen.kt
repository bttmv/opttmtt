package com.opengate.app.ui.screens.auction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opengate.app.data.currency.CurrencyManager
import com.opengate.app.data.model.AuctionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionListScreen(
    viewModel: AuctionListViewModel,
    onBack: () -> Unit,
    onOpenAuction: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("المزادات المباشرة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadAuctions) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "بدء مزاد")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Gavel, contentDescription = null) },
                text = { Text("بدء مزاد جديد", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading && state.auctions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.auctions.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "لا توجد مزادات مباشرة حالياً",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "كن أول من يطلق مزاداً مباشراً الآن، واعرض سلعتك للتنافس عليها لحظياً بين المشترين بأعلى سعر!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("بدء أول مزاد مباشر الآن 🔨", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            state.error?.let {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        items(state.auctions, key = { it.id }) { a ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAuction(a.id) }
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.Gavel,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            a.listings?.title ?: "مزاد معلن",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "السعر الحالي: ${CurrencyManager.formatNumber(a.current_price ?: a.starting_price)} ر.ي",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "أقل زيادة: ${CurrencyManager.formatNumber(a.min_increment)} ر.ي",
                                            color = MaterialTheme.colorScheme.outline,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    AssistChip(
                                        onClick = { onOpenAuction(a.id) },
                                        label = {
                                            Text(
                                                if (a.status == "live") "🔴 مباشر" else "⏱️ قادم",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAuctionDialog(
            creating = state.creating,
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, startPrice, minInc, hours, city, phone ->
                viewModel.startAuction(
                    title = title,
                    startingPrice = startPrice,
                    minIncrement = minInc,
                    durationHours = hours,
                    city = city,
                    contactPhone = phone,
                    onSuccess = { newId ->
                        showCreateDialog = false
                        onOpenAuction(newId)
                    }
                )
            }
        )
    }
}

@Composable
private fun CreateAuctionDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, startPrice: Double, minInc: Double, durationHours: Long, city: String?, phone: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startingPrice by remember { mutableStateOf("") }
    var minIncrement by remember { mutableStateOf("1000") }
    var durationHours by remember { mutableStateOf(24L) }
    var city by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("بدء مزاد مباشر جديد", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; validationError = null },
                    label = { Text("اسم السلعة المعروضة للمزاد *") },
                    placeholder = { Text("مثال: ساعة رولكس، سيارة إلنترا 2021...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startingPrice,
                        onValueChange = { startingPrice = it; validationError = null },
                        label = { Text("السعر الافتتاحي (ر.ي) *") },
                        placeholder = { Text("10000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minIncrement,
                        onValueChange = { minIncrement = it },
                        label = { Text("أقل زيادة (ر.ي)") },
                        placeholder = { Text("1000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف للتواصل") },
                    placeholder = { Text("77xxxxxxx") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("المدينة") },
                    placeholder = { Text("صنعاء، عدن...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("مدة المزاد المباشر:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        1L to "1 ساعة",
                        6L to "6 ساعات",
                        24L to "24 ساعة",
                        72L to "3 أيام"
                    ).forEach { (h, label) ->
                        FilterChip(
                            selected = durationHours == h,
                            onClick = { durationHours = h },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                validationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sPrice = startingPrice.toDoubleOrNull()
                    val mInc = minIncrement.toDoubleOrNull() ?: 1000.0
                    if (title.isBlank()) {
                        validationError = "يرجى كتابة اسم السلعة"
                        return@Button
                    }
                    if (sPrice == null || sPrice <= 0) {
                        validationError = "يرجى كتابة سعر افتتاحي صحيح"
                        return@Button
                    }
                    onSubmit(title.trim(), sPrice, mInc, durationHours, city.trim().ifBlank { null }, phone.trim().ifBlank { null })
                },
                enabled = !creating
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("إطلاق المزاد 🚀", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text("إلغاء")
            }
        }
    )
}
