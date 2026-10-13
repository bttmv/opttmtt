package com.opengate.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * شريط التنقل السفلي الفخم (Modern Bottom Navigation Bar):
 * يوفر وصولاً سريعاً ومريحاً لأهم وجهات التطبيق، مع زر وسطي متوهج وجذاب لرفع الإعلانات
 * ونبض أحمر على أيقونة المزادات المباشرة.
 */
@Composable
fun ModernBottomBar(
    onHomeClick: () -> Unit,
    onAuctionsClick: () -> Unit,
    onCreateClick: () -> Unit,
    onChatsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // حركة نبض شارة المزاد المباشر
    val infiniteTransition = rememberInfiniteTransition(label = "auction_dot")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. الرئيسية
            BottomNavItem(
                icon = Icons.Filled.Home,
                label = "الرئيسية",
                isSelected = true,
                onClick = onHomeClick
            )

            // 2. المزادات المباشرة مع شارة نابضة
            Box(contentAlignment = Alignment.TopEnd) {
                BottomNavItem(
                    icon = Icons.Filled.Gavel,
                    label = "المزادات",
                    isSelected = false,
                    onClick = onAuctionsClick
                )
                // شارة Live حمراء نابضة
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 12.dp)
                        .size(8.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }

            // 3. زر الإضافة المركزي المتوهج (Raised Glowing FAB)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .size(54.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onCreateClick
                    )
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "أضف إعلانك",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // 4. المحادثات
            BottomNavItem(
                icon = Icons.Filled.ChatBubbleOutline,
                label = "محادثاتي",
                isSelected = false,
                onClick = onChatsClick
            )

            // 5. المفضلة
            BottomNavItem(
                icon = Icons.Filled.FavoriteBorder,
                label = "المفضلة",
                isSelected = false,
                onClick = onFavoritesClick
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
