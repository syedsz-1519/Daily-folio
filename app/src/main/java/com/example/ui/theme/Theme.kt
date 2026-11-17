package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightEditorialScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracotta,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = SageSecondary,
    onSecondary = OnSage,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = OchreTertiary,
    onTertiary = OnOchre,
    tertiaryContainer = OchreContainer,
    onTertiaryContainer = OnOchreContainer,
    background = ParchmentBg,
    onBackground = InkPrimary,
    surface = ParchmentSurface,
    onSurface = InkPrimary,
    surfaceVariant = ParchmentSurfaceHigh,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color(0xFFFFFCF7),
    surfaceContainerLow = ParchmentBg,
    surfaceContainer = ParchmentSurface,
    surfaceContainerHigh = ParchmentSurfaceHigh,
    outline = HairlineOutline,
    outlineVariant = HairlineSubtle,
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkMidnightInkScheme = darkColorScheme(
    primary = WarmCoralPrimary,
    onPrimary = OnWarmCoral,
    primaryContainer = WarmCoralContainer,
    onPrimaryContainer = OnWarmCoralContainer,
    secondary = SageLightSecondary,
    onSecondary = OnSageLight,
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = OnSageDarkContainer,
    tertiary = AmberLightTertiary,
    onTertiary = OnAmberLight,
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = OnAmberDarkContainer,
    background = MidnightBg,
    onBackground = CreamInk,
    surface = MidnightSurface,
    onSurface = CreamInk,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = CreamInkMuted,
    surfaceContainerLowest = Color(0xFF110F0D),
    surfaceContainerLow = MidnightBg,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightSurfaceHigh,
    outline = MidnightOutline,
    outlineVariant = Color(0xFF332D26),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

val FolioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FolioNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkMidnightInkScheme else LightEditorialScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FolioTypography,
        shapes = FolioShapes,
        content = content
    )
}
