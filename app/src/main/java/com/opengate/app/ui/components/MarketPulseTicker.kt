package com.opengate.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * شريط النبض الحي المتدفق (Live Market Pulse & Ticker):
 * يمنح المستخدم إحساساً فورياً بوجود حركة بيع وشراء مستمرة وتواجد مئات المتسوقين والصفقات في هذه اللحظة.
 */
@Composable
fun MarketPulseTicker(
    extraSponsors: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val defaultPulseItems = remember {
        listOf(
            "🟢 متواجد الآن: 186 تاجر ومشتري يتصفحون السوق",
            "⚡ صفقة فورية: تم بيع سيارة تويوتا لاندكروزر في صنعاء",
            "🔥 مزاد مباشر جاري الآن - شارك برأيك وسعرك قبل انتهاء الوقت",
            "💵 أسعار الصرف الحية: الريال السعودي (عدن 445 | صنعاء 140.5)",
            "🏡 عروض عقارية وفلل جديدة معروضة للتواصل المباشر بدون عمولة",
            "💎 انضم أكثر من 45 معرضاً وتاجراً معتمداً إلى منصة OPEN GATE اليوم"
        )
    }

    val allItems = remember(extraSponsors) {
        if (extraSponsors.isEmpty()) defaultPulseItems else extraSponsors + defaultPulseItems
    }

    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(allItems) {
        while (true) {
            delay(3800)
            currentIndex = (currentIndex + 1) % allItems.size
        }
    }

    // حركة نبض النقطة الخضراء
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // مؤشر النبض الأخضر الحي
            Box(
                Modifier
                    .size(12.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = pulseAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF15803D))
                )
            }

            Spacer(Modifier.width(10.dp))

            // نص التحديث الحي مع حركة انسيابية
            AnimatedContent(
                targetState = allItems[currentIndex],
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "tickerAnimation"
            ) { targetText ->
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.ElectricBolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
