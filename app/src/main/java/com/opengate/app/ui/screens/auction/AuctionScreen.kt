package com.opengate.app.ui.screens.auction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opengate.app.data.currency.CurrencyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionScreen(viewModel: AuctionViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("مزاد مباشر") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        if (state.loading || state.auction == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val auction = state.auction!!

        Column(Modifier.padding(padding).imePadding().padding(16.dp).fillMaxSize()) {
            // السعر الحالي — يتحدث لحظياً بمجرد وصول أي مزايدة جديدة من أي مستخدم
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("السعر الحالي", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${CurrencyManager.formatNumber(auction.current_price ?: auction.starting_price)} ر.ي",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(formatCountdown(state.remainingSeconds), color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.bidInput, onValueChange = viewModel::onBidInputChange,
                    label = { Text("مزايدتك (بحد أدنى ${CurrencyManager.formatNumber(state.minNextBid)} ر.ي)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::placeBid, enabled = !state.placingBid) {
                    Text(if (state.placingBid) "..." else "زايد")
                }
            }
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Text("المزايدات الحية", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.bids) { bid ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (bid.bidder_id == state.myUserId) "أنت" else "مزايد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${CurrencyManager.formatNumber(bid.amount)} ر.ي", fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "انتهى المزاد"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "متبقٍ: %02d:%02d:%02d".format(h, m, s) else "متبقٍ: %02d:%02d".format(m, s)
}
