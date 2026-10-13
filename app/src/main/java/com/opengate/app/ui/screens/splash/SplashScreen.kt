package com.opengate.app.ui.screens.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opengate.app.util.WelcomeSoundPlayer
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val soundPlayer = remember { WelcomeSoundPlayer(context) }
    var isMuted by remember { mutableStateOf(false) }

    // التحكم بالدخول لمرة واحدة
    var hasNavigated by remember { mutableStateOf(false) }
    fun proceed() {
        if (!hasNavigated) {
            hasNavigated = true
            soundPlayer.stop()
            onFinished()
        }
    }

    DisposableEffect(Unit) {
        soundPlayer.start()
        onDispose {
            soundPlayer.stop()
        }
    }

    // حركة خلفية متقلبة ومتموجة مستمرة
    val infiniteTransition = rememberInfiniteTransition(label = "splashAnimation")

    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorShift"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // تقلب العبارات الترويجية والتعريفية دورياً
    val slogans = remember {
        listOf(
            "🚪 أوبن جيت • بوابتك المفتوحة للتجارة الحرة",
            "🔨 ساحة المزادات المباشرة والمزايدة الفورية",
            "🚗 سوق ومعارض السيارات والمحركات",
            "🏡 منصة العقارات والمخططات السكنية",
            "🛍️ السوق التجاري والإلكترونيات الشاملة",
            "🤝 صفقات مباشرة بدون أي وسيط أو عمولة"
        )
    }
    var sloganIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            sloganIndex = (sloganIndex + 1) % slogans.size
        }
    }

    // عداد تقدم زمني متحرك ينتهي بالدخول التلقائي
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3800, easing = LinearEasing)
        )
        proceed()
    }

    // خلفية متدرجة متحركة ديناميكياً
    val topColor = Color(0xFF090D16)
    val midColor = Color(
        red = (15 + colorShift * 12).toInt(),
        green = (23 + colorShift * 28).toInt(),
        blue = (42 + colorShift * 22).toInt()
    )
    val accentColor = Color(
        red = (30 + colorShift * 25).toInt(),
        green = (25 + colorShift * 18).toInt(),
        blue = (15 + colorShift * 8).toInt()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topColor, midColor, accentColor, topColor)
                )
            )
            .padding(24.dp)
    ) {
        // تأثير هالة ضوئية خلفية متموجة (Aura glow)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = glowAlpha * 0.18f),
                        Color(0xFFF59E0B).copy(alpha = glowAlpha * 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.55f
                )
            )
        }

        // زر كتم / تشغيل الصوت في الأعلى
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        isMuted = soundPlayer.toggleMute()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (isMuted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                        contentDescription = "الصوت",
                        tint = if (isMuted) Color.Gray else Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (isMuted) "صامت" else "صوت جهوري",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )

                    // موجات صوتية متحركة (Equalizer)
                    if (!isMuted) {
                        Spacer(Modifier.width(2.dp))
                        EqualizerWaves(infiniteTransition)
                    }
                }
            }

            TextButton(
                onClick = { proceed() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
            ) {
                Text("تخطي ❯", style = MaterialTheme.typography.labelMedium)
            }
        }

        // محتوى المركز: الشعار والهوية المتقلبة
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // أيقونة البوابة المشعة المتحركة
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
            ) {
                // هالة مشعة خارجية
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFF59E0B).copy(alpha = 0.6f),
                                    Color(0xFF38BDF8).copy(alpha = 0.6f),
                                    Color(0xFF10B981).copy(alpha = 0.6f),
                                    Color(0xFFF59E0B).copy(alpha = 0.6f)
                                )
                            )
                        )
                        .alpha(glowAlpha)
                )

                // قرص الأيقونة الداخلي
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(2.dp, Color(0xFFF59E0B).copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🚪",
                        fontSize = 48.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // اسم التطبيق بالإنجليزية بخط بارز
            Text(
                "OPEN GATE",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                letterSpacing = 4.sp,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(4.dp))

            // اسم التطبيق بالعربية ببريق ذهبي
            Text(
                "أوبــن جـيــت",
                color = Color(0xFFFBBF24),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(24.dp))

            // شريط العبارات المتقلبة والمتحركة (Animated Content)
            AnimatedContent(
                targetState = sloganIndex,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn()).togetherWith(
                        slideOutVertically { height -> -height } + fadeOut()
                    )
                },
                label = "sloganAnimation"
            ) { targetIndex ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = slogans[targetIndex],
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // أسفل الشاشة: شريط التقدم وزر الدخول المباشر
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // زر الدخول الفوري
            Button(
                onClick = { proceed() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp)
            ) {
                Text(
                    "دخول التطبيق الآن ❯",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // شريط التقدم الدائري / الخطي الناعم
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF38BDF8), Color(0xFFF59E0B))
                            )
                        )
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "بوابتك المفتوحة للتجارة والمزادات الحرة",
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * مكوّن موجات صوتية نابضة (Equalizer Bars)
 */
@Composable
private fun EqualizerWaves(transition: InfiniteTransition) {
    val bar1 by transition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 14f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 6f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(16.dp)
    ) {
        Box(Modifier.width(2.5.dp).height(bar1.dp).background(Color(0xFFF59E0B), RoundedCornerShape(1.dp)))
        Box(Modifier.width(2.5.dp).height(bar2.dp).background(Color(0xFF38BDF8), RoundedCornerShape(1.dp)))
        Box(Modifier.width(2.5.dp).height(bar3.dp).background(Color(0xFFF59E0B), RoundedCornerShape(1.dp)))
    }
}
