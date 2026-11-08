package com.dorp.pulse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dorp.pulse.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F0),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainer = Color(0xFFE9EFEE),
    surfaceContainerHigh = Color(0xFFE3E9E8),
    surfaceContainerHighest = Color(0xFFDDE4E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5D4),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF9CF1F0),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
)

private val BaseType = Typography()

private val AppTypography = BaseType.copy(
    displayLarge = BaseType.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
    displayMedium = BaseType.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineLarge = BaseType.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PulseTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
