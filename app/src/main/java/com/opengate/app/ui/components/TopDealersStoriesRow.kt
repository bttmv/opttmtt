package com.opengate.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class StoryItem(
    val id: String,
    val title: String,
    val icon: String,
    val isLive: Boolean = false,
    val isGold: Boolean = false,
    val badgeText: String? = null,
    val description: String,
    val tags: List<String> = emptyList()
)

/**
 * شريط القصص والمعارض المميزة (Market Stories):
 * يعطي إيحاءً حيوياً كمنصات التواصل والمتاجر العالمية الكبرى مع حلقات لونية متحركة.
 */
@Composable
fun TopDealersStoriesRow(
    onOpenAuctions: () -> Unit,
    onCreateListing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stories = remember {
        listOf(
            StoryItem(
                id = "auctions_live",
                title = "مزادات حية",
                icon = "🔨",
                isLive = true,
                badgeText = "مباشر",
                description = "مزادات علنية مباشرة بالثواني بين المشترين! تصفح السلع الحالية وتنافس برفع السعر أو ابدأ مزادك الخاص الآن.",
                tags = listOf("تحديث فوري", "بدون وسيط", "تنافس حي")
            ),
            StoryItem(
                id = "car_dealers",
                title = "معارض VIP",
                icon = "🚗",
                isGold = true,
                badgeText = "معتمد",
                description = "نخبة معارض السيارات في اليمن. سيارات جديدة ومستعملة مفحوصة مع إمكانية التواصل هاتفياً وواتساب فورياً مع أصحاب المعارض.",
                tags = listOf("معارض سيارات", "فحص مضمون", "تواصل واتساب")
            ),
            StoryItem(
                id = "real_estate",
                title = "عقارات النخبة",
                icon = "🏡",
                isGold = true,
                badgeText = "موثق",
                description = "فلل سكنية، أراضي استثمارية، وشقق للإيجار والتمليك في أرقى أحياء صنعاء وعدن مباشرة من المالك والمكاتب المعتمدة.",
                tags = listOf("فلل فاخرة", "أراضي استثمارية", "صنعاء وعدن")
            ),
            StoryItem(
                id = "gold_deals",
                title = "صفقات ذهبية",
                icon = "💎",
                badgeText = "لقطة",
                description = "إعلانات حصرية بأسعار لقطة لا تتكرر! سلع معروضة للبيع السريع والمباشر بأفضل قيمة.",
                tags = listOf("أفضل سعر", "فرص استثمارية", "بيع سريع")
            ),
            StoryItem(
                id = "electronics",
                title = "إلكترونيات",
                icon = "📱",
                description = "أحدث الهواتف الذكية وأجهزة اللابتوب والشاشات من متاجر وأفراد موثوقين.",
                tags = listOf("هواتف ذكية", "أجهزة لابتوب", "مستلزمات تقنية")
            ),
            StoryItem(
                id = "verified_sellers",
                title = "تجار موثوقون",
                icon = "🛡️",
                badgeText = "أمان",
                description = "حسابات تجار ومكاتب حاصلة على علامة التوثيق لضمان تعامل آمن وصادق 100%.",
                tags = listOf("حسابات معتمدة", "أمان مالي", "دعم مباشر")
            )
        )
    }

    var selectedStory by remember { mutableStateOf<StoryItem?>(null) }

    // حركة الدوران البطيء للتدرج اللوني للحلقات
    val infiniteTransition = rememberInfiniteTransition(label = "story_border")
    val borderRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(stories, key = { it.id }) { story ->
            StoryCircleItem(
                story = story,
                borderRotation = borderRotation,
                onClick = {
                    if (story.id == "auctions_live") {
                        onOpenAuctions()
                    } else {
                        selectedStory = story
                    }
                }
            )
        }
    }

    selectedStory?.let { story ->
        StoryDetailDialog(
            story = story,
            onDismiss = { selectedStory = null },
            onAction = {
                selectedStory = null
                if (story.id == "auctions_live") {
                    onOpenAuctions()
                } else {
                    onCreateListing()
                }
            }
        )
    }
}

@Composable
private fun StoryCircleItem(
    story: StoryItem,
    borderRotation: Float,
    onClick: () -> Unit
) {
    val gradientColors = when {
        story.isLive -> listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFFEF4444))
        story.isGold -> listOf(Color(0xFFEAB308), Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFEAB308))
        else -> listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primary
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // الحلقة المتوهجة المتحركة
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .rotate(borderRotation)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(gradientColors))
            )

            // الدائرة الداخلية البيضاء/الداكنة
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    story.icon,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // شارة المباشر أو التوثيق في الأسفل
            story.badgeText?.let { badge ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (story.isLive) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 4.dp)
                ) {
                    Text(
                        badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            story.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StoryDetailDialog(
    story: StoryItem,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(story.icon, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(story.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    story.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    story.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        if (story.id == "auctions_live") "دخول المزادات المباشرة 🔨" else "ابدأ الإعلان في هذا القسم 🚀",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
