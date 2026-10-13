package com.opengate.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opengate.app.data.model.RatingSummary
import com.opengate.app.data.model.Review

/** سطر نجوم تفاعلي لإدخال التقييم (1..5) */
@Composable
fun StarRatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { star ->
            val filled = star <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "$star نجوم",
                tint = if (filled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onRatingChange(star) }
            )
        }
    }
}

/** سطر ملخص التقييم: متوسط + عدد المراجعات */
@Composable
fun RatingSummaryRow(summary: RatingSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (summary.reviews_count > 0)
                "${summary.average_rating} (${summary.reviews_count} تقييم)"
            else
                "لا توجد تقييمات بعد",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** قسم كامل: نموذج إضافة تقييم + قائمة المراجعات الحالية */
@Composable
fun ReviewsSection(
    reviews: List<Review>,
    onSubmit: (rating: Int, comment: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Text("التقييمات والمراجعات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        StarRatingInput(rating = rating, onRatingChange = {
            rating = it
            validationError = null
        })
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text("شاركنا رأيك حول هذا الإعلان / السلعة...") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default,
            singleLine = false,
            maxLines = 3
        )

        validationError?.let { err ->
            Spacer(Modifier.height(6.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (rating <= 0) {
                    validationError = "يرجى تحديد عدد النجوم للتقييم (1 إلى 5 نجوم)"
                    return@Button
                }
                onSubmit(rating, comment)
                rating = 0
                comment = ""
                validationError = null
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("نشر التقييم", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        if (reviews.isEmpty()) {
            Text(
                "كن أول من يشارك برأيه ويقيّم هذه السلعة ⭐",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            reviews.forEach { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    RatingSummaryRow(RatingSummary(r.rating.toDouble(), 1))
                    r.comment?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
