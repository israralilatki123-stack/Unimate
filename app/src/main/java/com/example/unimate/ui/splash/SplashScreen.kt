package com.example.unimate.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unimate.R
import com.example.unimate.ui.components.UniMateLogoMark
import com.example.unimate.ui.theme.UniMateBlueElectric
import com.example.unimate.ui.theme.UniMateCyanAccent
import com.example.unimate.ui.theme.UniMateIndigoDeep
import com.example.unimate.ui.theme.UniMatePurplePrimary

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigate: (SplashDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.pendingDestination) {
        val destination = uiState.pendingDestination ?: return@LaunchedEffect
        viewModel.onNavigationConsumed()
        onNavigate(destination)
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "splash_logo_scale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splash_logo_alpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "splash_halo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo_pulse",
    )

    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                UniMateIndigoDeep,
                UniMatePurplePrimary,
                UniMateBlueElectric,
            ),
            start = Offset.Zero,
            end = Offset(1100f, 2200f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
            .testTag("splash_screen"),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        UniMateCyanAccent.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.14f),
                    radius = size.minDimension * 0.55f,
                ),
                center = Offset(size.width * 0.85f, size.height * 0.14f),
                radius = size.minDimension * 0.55f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.82f),
                    radius = size.minDimension * 0.65f,
                ),
                center = Offset(size.width * 0.15f, size.height * 0.82f),
                radius = size.minDimension * 0.65f,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(168.dp),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(haloScale)
                            .alpha(logoAlpha),
                    ) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.16f),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        drawCircle(
                            color = UniMateCyanAccent.copy(alpha = 0.12f),
                            radius = size.minDimension * 0.42f,
                        )
                    }

                    UniMateLogoMark(
                        size = 122.dp,
                        shadowElevation = 16.dp,
                        borderColor = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier
                            .scale(logoScale)
                            .alpha(logoAlpha)
                            .testTag("splash_logo"),
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                AnimatedVisibility(
                    visible = startAnimation,
                    enter = fadeIn(animationSpec = tween(durationMillis = 480, delayMillis = 120)) +
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(durationMillis = 480, delayMillis = 120),
                        ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("splash_app_name"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 0.4.sp,
                            ),
                            color = Color.White.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("splash_tagline"),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = startAnimation,
                enter = fadeIn(animationSpec = tween(durationMillis = 400, delayMillis = 240)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(136.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = UniMateCyanAccent,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.splash_loading_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.76f),
                    )
                }
            }
        }
    }
}
