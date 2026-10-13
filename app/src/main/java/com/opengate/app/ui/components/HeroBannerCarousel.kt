package com.opengate.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class BannerItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val buttonText: String,
    val gradientColors: List<Color>,
    val iconEmoji: String,
    val actionType: String // "auctions", "create", "exchange", "browse"
)

/**
 * سلايدر البانرات الفاخر التفاعلي (Hero Banner Carousel):
 * بانرات متحركة بتدرجات لونية فخمة تقلب تلقائياً مع أزرار نداء للعمل (CTA) تجذب المستخدم فوراً.
 */
@Composable
fun HeroBannerCarousel(
    onOpenAuctions: () -> Unit,
    onCreateListing: () -> Unit,
    onBrowseMarket: () -> Unit,
    modifier: Modifier = Modifier
) {
    val banners = remember {
        listOf(
            BannerItem(
                id = "auction_banner",
                title = "المزادات العلنية المباشرة",
                subtitle = "تنافس بالثواني واظفر بأفضل الصفقات لحظياً قبل إغلاق العداد!",
                badge = "🔴 مباشر وحصري",
                buttonText = "دخول المزادات الحية",
                gradientColors = listOf(Color(0xFF991B1B), Color(0xFFDC2626), Color(0xFFF97316)),
                iconEmoji = "🔨",
                actionType = "auctions"
            ),
            BannerItem(
                id = "create_banner",
                title = "اعرض سلعك واكسب زبائنك",
                subtitle = "آلاف المشترين يبحثون عن سيارات، عقارات، وإلكترونيات يومياً.",
                badge = "⚡ بيع فوري مجاني",
                buttonText = "أضف إعلانك الآن",
                gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF38BDF8)),
                iconEmoji = "🚀",
                actionType = "create"
            ),
            BannerItem(
                id = "exchange_banner",
                title = "أسعار الصرف المباشرة",
                subtitle = "تغطية فورية لأسواق صنعاء وعدن مع محول عملات دقيق.",
                badge = "💵 لحظة بلحظة",
                buttonText = "تصفح السوق والأسعار",
                gradientColors = listOf(Color(0xFF064E3B), Color(0xFF059669), Color(0xFF10B981)),
                iconEmoji = "📊",
                actionType = "browse"
            ),
            BannerItem(
                id = "direct_deal_banner",
                title = "تواصل مباشر وبدون وسيط",
                subtitle = "اتصال هاتفي ومراسلة واتساب بنقرة واحدة مع المالك الحقيقي.",
                badge = "🛡️ صفقات مضمونة",
                buttonText = "استكشف كل الأقسام",
                gradientColors = listOf(Color(0xFF581C87), Color(0xFF7C3AED), Color(0xFFA855F7)),
                iconEmoji = "🤝",
                actionType = "browse"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { banners.size })

    // تقليب تلقائي كل 4.5 ثانية
    LaunchedEffect(pagerState) {
        while (true) {
            delay(4500)
            val nextPage = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(nextPage, animationSpec = tween(600))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val banner = banners[page]
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (banner.actionType) {
                            "auctions" -> onOpenAuctions()
                            "create" -> onCreateListing()
                            else -> onBrowseMarket()
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(banner.gradientColors))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.28f)
                            ) {
                                Text(
                                    banner.badge,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(banner.iconEmoji, style = MaterialTheme.typography.headlineMedium)
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            banner.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            banner.subtitle,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )

                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            modifier = Modifier.clickable {
                                when (banner.actionType) {
                                    "auctions" -> onOpenAuctions()
                                    "create" -> onCreateListing()
                                    else -> onBrowseMarket()
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    banner.buttonText,
                                    color = Color(0xFF1E293B),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF1E293B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // مؤشرات الصفحات (Dots Indicator)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(banners.size) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (isSelected) 22.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                )
            }
        }
    }
}
