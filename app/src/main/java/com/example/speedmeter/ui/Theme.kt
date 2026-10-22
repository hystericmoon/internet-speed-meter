package com.example.speedmeter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedmeter.R

/**
 * shadcn/ui "zinc" tokens, mirrored 1:1 from its CSS variables so the vocabulary matches
 * the web kit (background, foreground, muted, border, ring...).
 */
@Immutable
data class ShadColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    /** shadcn's red-500 fails AA for text on white; these are tuned to pass 4.5:1. */
    val destructive: Color,
    val destructiveForeground: Color,
)

private val Light = ShadColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF09090B),
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF09090B),
    primary = Color(0xFF18181B),
    primaryForeground = Color(0xFFFAFAFA),
    secondary = Color(0xFFF4F4F5),
    secondaryForeground = Color(0xFF18181B),
    muted = Color(0xFFF4F4F5),
    mutedForeground = Color(0xFF71717A),
    border = Color(0xFFE4E4E7),
    input = Color(0xFFE4E4E7),
    ring = Color(0xFF18181B),
    destructive = Color(0xFFDC2626),
    destructiveForeground = Color(0xFFFAFAFA),
)

private val Dark = ShadColors(
    background = Color(0xFF09090B),
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF09090B),
    cardForeground = Color(0xFFFAFAFA),
    primary = Color(0xFFFAFAFA),
    primaryForeground = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    secondaryForeground = Color(0xFFFAFAFA),
    muted = Color(0xFF27272A),
    mutedForeground = Color(0xFFA1A1AA),
    border = Color(0xFF27272A),
    input = Color(0xFF27272A),
    ring = Color(0xFFD4D4D8),
    destructive = Color(0xFFF87171),
    destructiveForeground = Color(0xFF09090B),
)

/** shadcn --radius: 0.5rem, with its sm/md/lg/xl steps. */
object ShadRadius {
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
    val xl = 12.dp
}

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)
val GeistMono = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium))

/** Tailwind's text scale as used by shadcn (text-sm = 14/20, text-2xl = 24/32...). */
@Immutable
data class ShadType(
    val h1: TextStyle,
    val large: TextStyle,
    val p: TextStyle,
    val small: TextStyle,
    val muted: TextStyle,
    val label: TextStyle,
    val stat: TextStyle,
)

private val type = ShadType(
    h1 = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
    large = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    p = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    small = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    muted = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    // Mono keeps digits a fixed width so the live readout doesn't jitter.
    stat = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
)

private val LocalShadColors = staticCompositionLocalOf { Light }
private val LocalShadType = staticCompositionLocalOf { type }

object Shad {
    val colors: ShadColors @Composable get() = LocalShadColors.current
    val type: ShadType @Composable get() = LocalShadType.current
}

@Composable
fun ShadTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    // Map onto Material so stock pieces (ripples, text selection, Scaffold) pick up the tokens.
    val material = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.primaryForeground,
            secondary = c.secondary, onSecondary = c.secondaryForeground,
            background = c.background, onBackground = c.foreground,
            surface = c.card, onSurface = c.cardForeground,
            surfaceVariant = c.muted, onSurfaceVariant = c.mutedForeground,
            outline = c.border, outlineVariant = c.border,
            error = c.destructive, onError = c.destructiveForeground,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.primaryForeground,
            secondary = c.secondary, onSecondary = c.secondaryForeground,
            background = c.background, onBackground = c.foreground,
            surface = c.card, onSurface = c.cardForeground,
            surfaceVariant = c.muted, onSurfaceVariant = c.mutedForeground,
            outline = c.border, outlineVariant = c.border,
            error = c.destructive, onError = c.destructiveForeground,
        )
    }
    CompositionLocalProvider(LocalShadColors provides c, LocalShadType provides type) {
        MaterialTheme(
            colorScheme = material,
            typography = Typography(bodyLarge = type.p, bodyMedium = type.muted, labelLarge = type.small),
            content = content,
        )
    }
}
