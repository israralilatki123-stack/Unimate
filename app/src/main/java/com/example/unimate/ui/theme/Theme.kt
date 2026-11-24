package com.example.unimate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val UniMateLightColorScheme = lightColorScheme(
    primary = UniMatePurplePrimary,
    onPrimary = UniMateOnLightPrimary,
    primaryContainer = UniMateLightSurfaceVariant,
    onPrimaryContainer = UniMateIndigoDeep,
    secondary = UniMateBlueElectric,
    onSecondary = UniMateOnLightPrimary,
    secondaryContainer = ColorTokens.SecondaryContainerLight,
    onSecondaryContainer = UniMateIndigoDeep,
    tertiary = UniMateCyanAccent,
    onTertiary = UniMateOnLightBackground,
    background = UniMateLightBackground,
    onBackground = UniMateOnLightBackground,
    surface = UniMateLightSurface,
    onSurface = UniMateOnLightBackground,
    surfaceVariant = UniMateLightSurfaceVariant,
    onSurfaceVariant = UniMateOnLightSurfaceVariant,
    outline = UniMateOutlineLight,
    error = UniMateCoralError,
)

private val UniMateDarkColorScheme = darkColorScheme(
    primary = UniMateVioletSoft,
    onPrimary = UniMateDarkBackground,
    primaryContainer = UniMateDarkSurfaceVariant,
    onPrimaryContainer = UniMateOnDarkBackground,
    secondary = UniMateCyanAccent,
    onSecondary = UniMateDarkBackground,
    secondaryContainer = UniMateIndigoDeep,
    onSecondaryContainer = UniMateOnDarkBackground,
    tertiary = UniMateCyanAccent,
    onTertiary = UniMateDarkBackground,
    background = UniMateDarkBackground,
    onBackground = UniMateOnDarkBackground,
    surface = UniMateDarkSurface,
    onSurface = UniMateOnDarkBackground,
    surfaceVariant = UniMateDarkSurfaceVariant,
    onSurfaceVariant = UniMateOnDarkSurfaceVariant,
    outline = UniMateOutlineDark,
    error = UniMateCoralError,
)

private object ColorTokens {
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFDBEAFE)
}

val UniMateShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun UniMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> UniMateDarkColorScheme
        else -> UniMateLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UniMateTypography,
        shapes = UniMateShapes,
        content = content,
    )
}
