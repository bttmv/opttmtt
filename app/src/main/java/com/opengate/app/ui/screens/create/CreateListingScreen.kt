package com.opengate.app.ui.screens.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.opengate.app.data.model.Category
import com.opengate.app.data.model.CategoryFieldSchema
import com.opengate.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    viewModel: CreateListingViewModel,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    BackHandler { onBack() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_LISTING_IMAGES)
    ) { uris -> if (uris.isNotEmpty()) viewModel.onImagesPicked(uris) }

    val isOnline by com.opengate.app.data.offline.NetworkMonitor.isOnline.collectAsState()

    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            state.postSubmitMessage?.let { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
            onSubmitted()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("إعلان جديد", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1) اختيار المنصة
            Dropdown(
                label = "المنصة",
                items = state.platforms,
                selected = state.selectedPlatform,
                itemLabel = { it.name_ar },
                onSelect = viewModel::onPlatformSelected
            )

            Spacer(Modifier.height(12.dp))

            // 2) اختيار التصنيف — يظهر بعد اختيار المنصة
            if (state.selectedPlatform != null) {
                if (state.loadingCategories) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Dropdown(
                        label = "التصنيف",
                        items = state.categories,
                        selected = state.selectedCategory,
                        itemLabel = { it.name_ar },
                        onSelect = viewModel::onCategorySelected
                    )

                    if (state.isCustomCategory) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.customCategoryName,
                            onValueChange = viewModel::onCustomCategoryNameChange,
                            label = { Text("اكتب التصنيف المخصص *") },
                            placeholder = { Text("مثال: سيارة رياضية / كلاسيكية / قطع غيار...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val titlePlaceholder = when (state.selectedPlatform?.key) {
                "real_estate" -> "مثال: شقة للبيع 4 غرف تشطيب سوبر ديلوكس"
                "cars" -> "مثال: تويوتا لاندكروزر 2022 نظيف بحالة الوكالة"
                "auctions" -> "مثال: مزاد على ساعة رولكس أصلية بحالة ممتازة"
                else -> "مثال: آيفون 15 برو ماكس 256 جيجا شبه جديد"
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("عنوان الإعلان *") },
                placeholder = { Text(titlePlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.price,
                onValueChange = viewModel::onPriceChange,
                label = {
                    val symbol = when (state.currency) {
                        "USD" -> "$"
                        "SAR" -> "ر.س"
                        else -> "ر.ي"
                    }
                    Text("السعر المطلوب ($symbol)")
                },
                placeholder = { Text("أدخل السعر (اختياري)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "YER" to "🇾🇪 ريال يمني",
                    "USD" to "💵 دولار",
                    "SAR" to "🇸🇦 سعودي"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = state.currency == code,
                        onClick = { viewModel.onCurrencyChange(code) },
                        label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.city,
                onValueChange = viewModel::onCityChange,
                label = { Text("المدينة / المحافظة") },
                placeholder = { Text("مثال: صنعاء، عدن، تعز...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.contactPhone,
                onValueChange = viewModel::onContactPhoneChange,
                label = { Text("رقم الهاتف / واتساب للتواصل") },
                placeholder = { Text("مثال: 771234567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("تفاصيل ووصف الإعلان") },
                placeholder = { Text("اكتب وصفاً تفصيلياً عن السلعة، حالتها، ومميزاتها...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // 3) الحقول الديناميكية حسب التصنيف
            if (state.loadingFields) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            } else if (state.fieldSchemas.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("تفاصيل ${state.selectedCategory?.name_ar ?: ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                state.fieldSchemas.forEach { field ->
                    DynamicField(
                        field = field,
                        value = state.dynamicValues[field.field_key] ?: "",
                        onValueChange = { viewModel.onDynamicValueChange(field.field_key, it) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("صور الإعلان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "يمكنك إضافة حتى $MAX_LISTING_IMAGES صور لجذب المشترين",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Box(
                        Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = "إضافة صور", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text("أضف صورة", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(state.images) { uri ->
                    Box(Modifier.size(90.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                        )
                        IconButton(
                            onClick = { viewModel.removeImage(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "حذف", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!isOnline) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "أنت في وضع عدم الاتصال: سيتم حفظ الإعلان وصوره على هاتفك ورفعه ونشره تلقائياً فور توفر الإنترنت.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.submit { uri ->
                        withContext(Dispatchers.IO) {
                            compressImageUri(context, uri)
                        }
                    }
                },
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.submitting || state.uploadingImages) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when {
                        state.uploadingImages -> "جاري رفع الصور..."
                        state.submitting -> "جاري الحفظ..."
                        !isOnline -> "⚡ حفظ الإعلان للمزامنة التلقائية (أوفلاين)"
                        else -> "نشر الإعلان الآن"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

private const val MAX_SOURCE_BYTES = 25L * 1024 * 1024   // لا نفتح صوراً أكبر من 25MB
private const val MAX_IMAGE_DIMENSION = 1600
private const val JPEG_QUALITY = 82

/**
 * يقرأ الصورة بذاكرة آمنة (قراءة الأبعاد أولاً ثم inSampleSize)، يصحّح الاتجاه من EXIF،
 * ثم يعيد ترميزها JPEG بحجم معقول. إعادة الترميز تُزيل بيانات EXIF (ومنها موقع التصوير GPS) حمايةً للخصوصية.
 * يرجع مصفوفة فارغة عند أي فشل أو إن كان الملف ليس صورة.
 */
private fun compressImageUri(context: Context, uri: Uri): ByteArray {
    return runCatching {
        val resolver = context.contentResolver

        // 1) حجم الملف
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size > MAX_SOURCE_BYTES) return@runCatching ByteArray(0)

        // 2) الأبعاد فقط
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching ByteArray(0)

        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_DIMENSION * 2 || bounds.outHeight / sample > MAX_IMAGE_DIMENSION * 2) sample *= 2

        // 3) فك الترميز بعينة مخفّضة
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching ByteArray(0)

        // 4) اتجاه الصورة من EXIF
        val orientation = resolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        val rotation = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // 5) تصغير نهائي + تدوير
        val ratio = minOf(1f, MAX_IMAGE_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = android.graphics.Matrix().apply {
            if (ratio < 1f) postScale(ratio, ratio)
            if (rotation != 0f) postRotate(rotation)
        }
        val finalBmp = if (ratio < 1f || rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else decoded

        val out = ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (finalBmp !== decoded) finalBmp.recycle()
        decoded.recycle()
        out.toByteArray()
    }.getOrDefault(ByteArray(0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicField(field: CategoryFieldSchema, value: String, onValueChange: (String) -> Unit) {
    val label = field.label_ar + if (field.is_required) " *" else ""
    when (field.field_type) {
        "number" -> OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        "phone" -> OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        "url" -> OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = value == "true", onCheckedChange = { onValueChange(it.toString()) })
        }
        "choice_binary" -> {
            val opts = field.options?.takeIf { it.isNotEmpty() } ?: listOf("نعم", "لا")
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opts.forEach { opt ->
                        FilterChip(
                            selected = value == opt,
                            onClick = { onValueChange(opt) },
                            label = {
                                Text(
                                    opt,
                                    fontWeight = if (value == opt) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = value, onValueChange = {}, readOnly = true,
                    label = { Text(label) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.options?.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { onValueChange(opt); expanded = false })
                    }
                }
            }
        }
        "multi_select" -> {
            // القيم المختارة تُخزَّن مفصولة بـ "، "
            val selected = value.split("، ").filter { it.isNotBlank() }.toSet()
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    field.options.orEmpty().forEach { opt ->
                        FilterChip(
                            selected = opt in selected,
                            onClick = {
                                val next = if (opt in selected) selected - opt else selected + opt
                                // نحافظ على ترتيب الخيارات كما عرّفها المالك
                                onValueChange(field.options.orEmpty().filter { it in next }.joinToString("، "))
                            },
                            label = { Text(opt) }
                        )
                    }
                }
            }
        }
        "textarea" -> OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) }, minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        "date" -> OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) }, placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        else -> {
            val placeholderText = if (field.field_key == "model" || field.label_ar.contains("موديل")) "مثال: 2022 أو لاندكروزر 2022" else ""
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = if (placeholderText.isNotEmpty()) { { Text(placeholderText) } } else null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(itemLabel) ?: "",
            onValueChange = {}, readOnly = true,
            label = { Text(label) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(itemLabel(item)) }, onClick = { onSelect(item); expanded = false })
            }
        }
    }
}
