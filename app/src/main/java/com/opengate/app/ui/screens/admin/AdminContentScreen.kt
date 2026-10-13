package com.opengate.app.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opengate.app.data.model.SocialLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminContentScreen(viewModel: AdminContentViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("التواصل والشريط المتحرك") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            item { Text("حسابات التواصل الاجتماعي", style = MaterialTheme.typography.titleMedium) }
            item {
                Text(
                    "كلها فارغة ومخفية افتراضياً — لا يراها الجمهور حتى تدخل الرابط وتفعّل الإظهار",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.socialLinks, key = { it.key }) { link ->
                SocialLinkCard(link = link, onSave = { value, visible -> viewModel.saveSocial(link.key, value, visible) })
            }

            item { Spacer(Modifier.height(8.dp)); Text("الشريط المتحرك (معارض ومكاتب مميزة)", style = MaterialTheme.typography.titleMedium) }
            items(state.sponsors, key = { it.id }) { s ->
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.text_ar, Modifier.weight(1f))
                        Switch(checked = s.is_visible, onCheckedChange = { viewModel.toggleSponsor(s) })
                        IconButton(onClick = { viewModel.deleteSponsor(s) }) { Text("🗑️") }
                    }
                }
            }
            item { AddSponsorForm(onAdd = viewModel::addSponsor) }
        }
    }
}

@Composable
private fun SocialLinkCard(link: SocialLink, onSave: (value: String, visible: Boolean) -> Unit) {
    var value by remember(link.key, link.value) { mutableStateOf(link.value) }
    var visible by remember(link.key, link.is_visible) { mutableStateOf(link.is_visible) }
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(link.label_ar, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("الرابط أو المعرّف") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("إظهار للجمهور", Modifier.weight(1f))
                Switch(checked = visible, onCheckedChange = { visible = it })
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(value, visible) }) { Text("حفظ") }
            }
        }
    }
}

@Composable
private fun AddSponsorForm(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Card {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("نص جديد (مثال: 🏆 معرض النخبة — الأعلى مبيعاً)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("إضافة للشريط") }
        }
    }
}
