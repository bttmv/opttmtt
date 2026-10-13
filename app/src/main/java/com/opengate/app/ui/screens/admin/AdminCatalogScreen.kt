package com.opengate.app.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val FIELD_TYPES = listOf(
    "text" to "نص عادي",
    "number" to "رقم",
    "choice_binary" to "خيار ثنائي بين قيمتين (مثال: جديد/مستعمل أو نعم/لا)",
    "select" to "قائمة اختيار منسدلة",
    "boolean" to "مفتاح تشغيل (نعم / لا)",
    "multi_select" to "تحديد أكثر من خيار",
    "date" to "تاريخ",
    "phone" to "هاتف",
    "url" to "رابط",
    "textarea" to "نص طويل"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCatalogScreen(viewModel: AdminCatalogViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("التصنيفات والحقول") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            item { Text("١) اختر المنصة", fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.platforms) { p ->
                        FilterChip(
                            selected = state.selectedPlatform?.id == p.id,
                            onClick = { viewModel.selectPlatform(p) },
                            label = { Text(p.name_ar) }
                        )
                    }
                }
            }

            if (state.selectedPlatform != null) {
                item { Text("٢) التصنيفات", fontWeight = FontWeight.SemiBold) }
                items(state.categories) { c ->
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.selectCategory(c) }) {
                                Text(
                                    (if (state.selectedCategory?.id == c.id) "◉ " else "○ ") + c.name_ar,
                                    fontWeight = if (state.selectedCategory?.id == c.id) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Switch(checked = c.is_visible, onCheckedChange = { viewModel.toggleCategory(c) })
                        }
                    }
                }
                item { AddCategoryForm(onAdd = viewModel::addCategory) }
            }

            if (state.selectedCategory != null) {
                item { Text("٣) حقول «${state.selectedCategory!!.name_ar}»", fontWeight = FontWeight.SemiBold) }
                items(state.fields) { f ->
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(f.label_ar, fontWeight = FontWeight.SemiBold)
                                Text(
                                    (FIELD_TYPES.firstOrNull { it.first == f.field_type }?.second ?: f.field_type) +
                                        if (f.is_required) " • إلزامي" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { viewModel.deleteField(f) }) { Text("🗑️") }
                        }
                    }
                }
                item { AddFieldForm(onAdd = viewModel::addField) }
            }
        }
    }
}

@Composable
private fun AddCategoryForm(onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Card {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم تصنيف جديد") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onAdd(name); name = "" }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("إضافة التصنيف") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldForm(onAdd: (label: String, type: String, required: Boolean, options: String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FIELD_TYPES.first()) }
    var required by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.padding(12.dp)) {
            Text("إضافة حقل جديد", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("اسم الحقل (مثال: عدد الأشرعة)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = type.second, onValueChange = {}, readOnly = true,
                    label = { Text("نوع الحقل") }, modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    FIELD_TYPES.forEach { t -> DropdownMenuItem(text = { Text(t.second) }, onClick = { type = t; expanded = false }) }
                }
            }
            if (type.first == "choice_binary") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = options,
                    onValueChange = { options = it },
                    label = { Text("الخياران مفصولان بـ / أو فاصلة") },
                    placeholder = { Text("مثال: جديد / مستعمل أو نعم / لا") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (type.first == "select" || type.first == "multi_select") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = options,
                    onValueChange = { options = it },
                    label = { Text("الخيارات مفصولة بفاصلة") },
                    placeholder = { Text("مثال: خيار 1, خيار 2, خيار 3") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("حقل إلزامي", Modifier.weight(1f))
                Switch(checked = required, onCheckedChange = { required = it })
            }
            Button(
                onClick = { onAdd(label, type.first, required, options); label = ""; options = ""; required = false },
                enabled = label.isNotBlank(), modifier = Modifier.fillMaxWidth()
            ) { Text("إضافة الحقل") }
        }
    }
}
