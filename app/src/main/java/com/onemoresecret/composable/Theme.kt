package com.onemoresecret.composable

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemoresecret.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

val DarkSecurityColorScheme = darkColorScheme(
    primary = Color(0xFF26C6DA),
    onPrimary = Color(0xFF0C1116),
    primaryContainer = Color(0xFF5CE1E6),
    onPrimaryContainer = Color(0xFF0C1116),
    secondary = Color(0xFF26C6DA),
    onSecondary = Color(0xFF0C1116),
    secondaryContainer = Color(0xFF182A31), // Dark cyan-tinted background for selected chips
    onSecondaryContainer = Color(0xFF26C6DA),
    tertiary = Color(0xFF21C55D),
    onTertiary = Color(0xFF0C1116),
    tertiaryContainer = Color(0xFF142B24),
    onTertiaryContainer = Color(0xFF21C55D),
    background = Color(0xFF0C1116),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF141A22),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF2B333E),
    onSurfaceVariant = Color(0xFF828C9A),
    outline = Color(0xFF828C9A), // Lighter slate for visible Switch thumbs and text field borders
    outlineVariant = Color(0xFF2B333E),
    surfaceTint = Color(0xFF26C6DA),
    inversePrimary = Color(0xFF00838F),
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0C1116),
    scrim = Color(0xFF000000),
    error = Color(0xFFE11D48),
    onError = Color(0xFFFFFFFF)
)

val LightSecurityColorScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF006064),
    secondary = Color(0xFF00838F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF00838F),
    tertiary = Color(0xFF16A34A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF166534),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceTint = Color(0xFF00838F),
    inversePrimary = Color(0xFF26C6DA),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF8FAFC),
    scrim = Color(0xFF000000),
    error = Color(0xFFE11D48),
    onError = Color(0xFFFFFFFF)
)

val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp)
)

@Composable
fun OneMoreSecretTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        DarkSecurityColorScheme
    } else {
        LightSecurityColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = { Surface(color = MaterialTheme.colorScheme.background, content = content) }
    )
}