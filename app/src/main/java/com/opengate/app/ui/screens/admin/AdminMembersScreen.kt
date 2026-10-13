package com.opengate.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opengate.app.data.model.AdminUserRow
import com.opengate.app.data.model.Role
import com.opengate.app.data.model.SYSTEM_PERMISSIONS
import com.opengate.app.data.model.SystemPermissionInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMembersScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editRoleAdmin by remember { mutableStateOf<AdminUserRow?>(null) }
    var permissionsAdmin by remember { mutableStateOf<AdminUserRow?>(null) }
    var deleteAdminTarget by remember { mutableStateOf<AdminUserRow?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("👥 أعضاء الإدارة والصلاحيات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isOwner) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("إضافة عضو جديد") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // شريط إحصائيات أعضاء الإدارة
            item {
                val totalAdmins = state.admins.size
                val activeCount = state.admins.count { it.is_active }
                val frozenCount = state.admins.count { !it.is_active }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SupervisorAccount, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "لوحة تحكم أعضاء فريق الإدارة والمشرفين",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "يمكنك من هنا استعراض كافة أعضاء الإدارة، تجميد أو تنشيط أي عضو، تعديل دوره، تخصيص الصلاحيات الدقيقة بتأشيرها، أو إزالته نهائياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatChip(label = "الإجمالي", count = totalAdmins, color = MaterialTheme.colorScheme.primary)
                            StatChip(label = "نشط ومفعّل", count = activeCount, color = Color(0xFF16A34A))
                            StatChip(label = "مُجمّد", count = frozenCount, color = Color(0xFFDC2626))
                        }
                    }
                }
            }

            state.message?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (state.admins.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.PeopleOutline, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("لا يوجد أعضاء إدارة مسجلين حالياً", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("اضغط على زر «إضافة عضو جديد» لتعيين مشرف جديد بالبريد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(state.admins) { admin ->
                    AdminMemberItemCard(
                        admin = admin,
                        isOwner = state.isOwner,
                        onToggleActive = { viewModel.toggleAdminActive(admin) },
                        onEditRole = { editRoleAdmin = admin },
                        onEditPermissions = { permissionsAdmin = admin },
                        onRemove = { deleteAdminTarget = admin }
                    )
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    // نافذة إضافة مشرف جديد
    if (showAddDialog) {
        AddAdminDialog(
            roles = state.roles,
            onDismiss = { showAddDialog = false },
            onConfirm = { email, roleId ->
                viewModel.addAdmin(email, roleId)
                showAddDialog = false
            }
        )
    }

    // نافذة تغيير الدور
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

    // نافذة تأكيد الحذف
    deleteAdminTarget?.let { admin ->
        AlertDialog(
            onDismissRequest = { deleteAdminTarget = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("تأكيد إزالة المشرف") },
            text = {
                Text("هل أنت متأكد من رغبتك في سحب كافة صلاحيات الإشراف عن هذا الحساب وإزالته من الإدارة نهائياً؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeAdmin(admin)
                        deleteAdminTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، إزالة العضو")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAdminTarget = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text("$count", fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun AdminMemberItemCard(
    admin: AdminUserRow,
    isOwner: Boolean,
    onToggleActive: () -> Unit,
    onEditRole: () -> Unit,
    onEditPermissions: () -> Unit,
    onRemove: () -> Unit
) {
    val isItemOwner = admin.roles?.key == "owner"
    val roleTitle = admin.roles?.name_ar ?: "مشرف"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isItemOwner) Color(0xFFFEF3C7)
            else if (admin.is_active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isItemOwner) Color(0xFFFDE68A) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isItemOwner) Icons.Filled.Stars else Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (isItemOwner) Color(0xFFB45309) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            roleTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isItemOwner) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "المعرف: ${admin.user_id.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isItemOwner) Color(0xFFFDE68A)
                    else if (admin.is_active) Color(0xFFDCFCE7)
                    else Color(0xFFFEE2E2)
                ) {
                    Text(
                        if (isItemOwner) "👑 المالك"
                        else if (admin.is_active) "نشط مفعّل ✓"
                        else "مُجمّد مؤقتاً ❄️",
                        color = if (isItemOwner) Color(0xFF92400E)
                        else if (admin.is_active) Color(0xFF166534)
                        else Color(0xFF991B1B),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!isItemOwner) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(10.dp))

                // صف الأزرار الرئيسية للتحكم بالعضو
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // زر الصلاحيات الكاملة (تأشير/إلغاء تأشير)
                    Button(
                        onClick = onEditPermissions,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("🛡️ الصلاحيات", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    // زر التجميد / التنشيط
                    OutlinedButton(
                        onClick = onToggleActive,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        colors = if (!admin.is_active) ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF16A34A)) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(
                            if (admin.is_active) "❄️ تجميد" else "▶️ تنشيط",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // زر تعديل الدور
                    TextButton(
                        onClick = onEditRole,
                        modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("الدور", style = MaterialTheme.typography.bodySmall)
                    }

                    // زر الحذف
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "حذف المشرف", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * اللوحة الكاملة للصلاحيات (بتأشيرها / إلغاء تأشيرها)
 * تتيح للمالك تحديد الصلاحيات بدقة متناهية مع وصف كل صلاحية
 */
@Composable
fun AdminPermissionsChecklistDialog(
    admin: AdminUserRow,
    onDismiss: () -> Unit,
    onLoadPermissions: suspend () -> Set<String>,
    onSavePermissions: (Set<String>) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(admin) {
        loading = true
        runCatching {
            val perms = onLoadPermissions()
            selectedKeys = perms
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🛡️ لوحة تخصيص الصلاحيات", fontWeight = FontWeight.Bold)
                Text(
                    "المشرف: ${admin.roles?.name_ar ?: "مشرف"} (${admin.user_id.take(8)}...)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "أشّر على الصلاحيات التي ترغب بمنحها لهذا المشرف، وألغِ التأشير لسحبها منه:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    // أزرار سريعة لتحديد الكل أو إلغاء الكل
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedKeys = SYSTEM_PERMISSIONS.map { it.key }.toSet() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("تحديد الكل ✓", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { selectedKeys = emptySet() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("إلغاء الكل ✕", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()

                    // قائمة الصلاحيات الكاملة مع مربعات التأشير
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(SYSTEM_PERMISSIONS) { perm ->
                            val isChecked = selectedKeys.contains(perm.key)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedKeys = if (isChecked) selectedKeys - perm.key else selectedKeys + perm.key
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedKeys = if (checked) selectedKeys + perm.key else selectedKeys - perm.key
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        perm.title,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        perm.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSavePermissions(selectedKeys) },
                enabled = !loading
            ) {
                Text("حفظ الصلاحيات المحددة ✓")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAdminDialog(
    roles: List<Role>,
    onDismiss: () -> Unit,
    onConfirm: (email: String, roleId: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    val assignableRoles = roles.filter { it.key != "owner" }
    var selectedRole by remember { mutableStateOf<Role?>(assignableRoles.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ إضافة وتعيين مشرف جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "أدخل البريد الإلكتروني للمستخدم المسجل في التطبيق ثم اختر الدور المبدئي:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني *") },
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
                        label = { Text("الدور المبدئي *") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedRole?.let { onConfirm(email, it.id) }
                },
                enabled = email.isNotBlank() && selectedRole != null
            ) {
                Text("إضافة المشرف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

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
        title = { Text("تعديل دور المشرف") },
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
