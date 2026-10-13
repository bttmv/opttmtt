package com.opengate.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opengate.app.data.model.AdminUserRow
import com.opengate.app.data.model.Platform
import com.opengate.app.data.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenModeration: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    var editRoleAdmin by remember { mutableStateOf<AdminUserRow?>(null) }
    var permissionsAdmin by remember { mutableStateOf<AdminUserRow?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isOwner) "👑 لوحة تحكم مالك المشروع"
                        else "🛡️ لوحة المشرف العام",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // شريط إشعار الدور الحالي
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isOwner) Color(0xFFFEF3C7) else Color(0xFFE0F2FE)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.isOwner) Icons.Filled.Stars else Icons.Filled.Security,
                            contentDescription = null,
                            tint = if (state.isOwner) Color(0xFFB45309) else Color(0xFF0369A1),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (state.isOwner) "حساب مالك المشروع (المدير الأعلى)" else "حساب مشرف: ${state.myRoleName}",
                                fontWeight = FontWeight.Bold,
                                color = if (state.isOwner) Color(0xFF92400E) else Color(0xFF075985)
                            )
                            Text(
                                if (state.isOwner) "تمتلك كامل الصلاحيات لإدارة المنصات، المشرفين، الحظر، والتصنيفات"
                                else "صلاحياتك محددة بالمهام الإشرافية الممنوحة لك من قبل المالك فقط",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.isOwner) Color(0xFFB45309) else Color(0xFF0284C7)
                            )
                        }
                    }
                }
            }

            state.error?.let {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            state.message?.let {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // أزرار الانتقال السريع للأقسام
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.canManageAdmins) {
                        Button(
                            onClick = onOpenMembers,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("👥 أعضاء الإدارة والصلاحيات (تجميد، تعديل، إزالة)", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.canModerate) {
                        Button(
                            onClick = onOpenModeration,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🕵️ مراجعة الإعلانات المعلّقة (اعتماد / رفض)", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (state.canManageCategories) {
                            OutlinedButton(
                                onClick = onOpenCatalog,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("📁 التصنيفات والحقول")
                            }
                        }
                        if (state.canManageContent) {
                            OutlinedButton(
                                onClick = onOpenContent,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("📢 التواصل والإعلانات")
                            }
                        }
                    }
                }
            }

            // قسم إدارة المشرفين والصلاحيات (خاص بمالك المشروع حصرياً)
            if (state.canManageAdmins) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "👑 فريق الإدارة والمشرفين",
                            subtitle = "خاص بالمالك: تعديل الصلاحيات بتأشيرها، التجميد، أو الإزالة"
                        )
                        TextButton(onClick = onOpenMembers) {
                            Text("عرض الكل (${state.admins.size}) ❯", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (state.admins.isEmpty()) {
                    item {
                        Text("لا يوجد مشرفين مضافين حالياً", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(state.admins) { admin ->
                        AdminUserCard(
                            admin = admin,
                            roles = state.roles,
                            onToggleActive = { viewModel.toggleAdminActive(admin) },
                            onEditRole = { editRoleAdmin = admin },
                            onEditPermissions = { permissionsAdmin = admin },
                            onRemove = { viewModel.removeAdmin(admin) }
                        )
                    }
                }

                item {
                    AddAdminCard(
                        roles = state.roles,
                        onAdd = viewModel::addAdmin
                    )
                }
            }

            // قسم إدارة المنصات (إظهار، إخفاء، واستحداث منصات جديدة)
            if (state.canManagePlatforms) {
                item {
                    SectionHeader(
                        title = "🏷️ إدارة المنصات وأقسام السوق",
                        subtitle = "التحكم في ظهور المنصات واستحداث منصات جديدة للمتجر"
                    )
                }

                items(state.platforms) { platform ->
                    PlatformAdminCard(
                        platform = platform,
                        onToggle = { viewModel.togglePlatform(platform) }
                    )
                }

                item {
                    AddPlatformCard(onAdd = viewModel::addPlatform)
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    // نافذة تعديل دور المشرف
    editRoleAdmin?.let { admin ->
        ChangeRoleDialog(
            admin = admin,
            roles = state.roles,
            onDismiss = { editRoleAdmin = null },
            onConfirm = { newRoleId ->
                viewModel.updateAdminRole(admin, newRoleId)
                editRoleAdmin = null
            }
        )
    }

    // نافذة لوحة الصلاحيات الكاملة بتأشيرها (Checkboxes)
    permissionsAdmin?.let { admin ->
        AdminPermissionsChecklistDialog(
            admin = admin,
            onDismiss = { permissionsAdmin = null },
            onLoadPermissions = { viewModel.getAdminPermissions(admin) },
            onSavePermissions = { updatedKeys ->
                viewModel.updateAdminPermissions(admin, updatedKeys)
                permissionsAdmin = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlatformAdminCard(platform: Platform, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (platform.is_visible) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(platform.icon ?: "🏷️", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(platform.name_ar, fontWeight = FontWeight.Bold)
                    Text(
                        if (platform.is_visible) "ظاهرة للمستخدمين ✓" else "مخفية عن المستخدمين ✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (platform.is_visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Switch(
                checked = platform.is_visible,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun AddPlatformCard(onAdd: (name: String, icon: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val presetIcons = listOf("🏍️", "🚗", "🏡", "📱", "💼", "🔨", "🛥️", "🎁", "⚙️", "👕")

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("➕ استحداث منصة جديدة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم المنصة الجديدة *") },
                placeholder = { Text("مثال: دراجات نارية وسكوترات") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = icon,
                onValueChange = { icon = it },
                label = { Text("رمز المنصة التعبيري (Emoji)") },
                placeholder = { Text("مثال: 🏍️") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetIcons.take(6).forEach { emoji ->
                    FilterChip(
                        selected = icon == emoji,
                        onClick = { icon = emoji },
                        label = { Text(emoji) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onAdd(name, icon)
                    name = ""
                    icon = ""
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إضافة المنصة وتفعيلها الآن", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminUserCard(
    admin: AdminUserRow,
    roles: List<Role>,
    onToggleActive: () -> Unit,
    onEditRole: () -> Unit,
    onEditPermissions: () -> Unit,
    onRemove: () -> Unit
) {
    val isOwner = admin.roles?.key == "owner"
    val roleTitle = admin.roles?.name_ar ?: "مشرف"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!admin.is_active) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isOwner) Icons.Filled.Stars else Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (isOwner) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            roleTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            if (isOwner) "مالك النظام الرئيسي" else if (admin.is_active) "حساب نشط ومفعّل ✓" else "حساب مُجمّد مؤقتاً ❄️",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!admin.is_active) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isOwner) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (admin.is_active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                    ) {
                        Text(
                            if (admin.is_active) "نشط" else "مُجمّد ❄️",
                            color = if (admin.is_active) Color(0xFF166534) else Color(0xFF991B1B),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!isOwner) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // زر تخصيص الصلاحيات الكاملة بتأشيرها
                    FilledTonalButton(
                        onClick = onEditPermissions,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🛡️ الصلاحيات", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.width(6.dp))

                    // زر التجميد / التنشيط
                    TextButton(onClick = onToggleActive) {
                        Text(if (admin.is_active) "❄️ تجميد" else "▶️ تنشيط")
                    }

                    Spacer(Modifier.width(6.dp))

                    // زر تعديل الدور
                    TextButton(onClick = onEditRole) {
                        Text("✏️ الدور")
                    }

                    Spacer(Modifier.width(6.dp))

                    // زر سحب الصلاحيات نهائياً
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "سحب الصلاحية", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAdminCard(
    roles: List<Role>,
    onAdd: (email: String, roleId: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf<Role?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val assignableRoles = roles.filter { it.key != "owner" }

    LaunchedEffect(roles) {
        if (selectedRole == null && assignableRoles.isNotEmpty()) {
            selectedRole = assignableRoles.first()
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("➕ منح وتعيين مشرف جديد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                "أدخل بريد المستخدم المسجل بالتطبيق وحدد صلاحياته ودوره",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني للمستخدم *") },
                placeholder = { Text("user@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedRole?.name_ar ?: "اختر الدور",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("الدور الإشرافي والصلاحيات *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    assignableRoles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name_ar) },
                            onClick = {
                                selectedRole = role
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    selectedRole?.let { onAdd(email, it.id) }
                    email = ""
                },
                enabled = email.isNotBlank() && selectedRole != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("منح الصلاحية وتعيين المشرف", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeRoleDialog(
    admin: AdminUserRow,
    roles: List<Role>,
    onDismiss: () -> Unit,
    onConfirm: (newRoleId: String) -> Unit
) {
    val assignableRoles = roles.filter { it.key != "owner" }
    var selectedRole by remember { mutableStateOf(assignableRoles.find { it.id == admin.role_id } ?: assignableRoles.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل دور وصلاحيات المشرف") },
        text = {
            Column {
                Text("اختر الدور الجديد المخصص لهذا المشرف:")
                Spacer(Modifier.height(10.dp))
                assignableRoles.forEach { role ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole?.id == role.id,
                            onClick = { selectedRole = role }
                        )
                        Text(role.name_ar, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedRole?.let { onConfirm(it.id) } },
                enabled = selectedRole != null
            ) {
                Text("تحديث الدور")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
