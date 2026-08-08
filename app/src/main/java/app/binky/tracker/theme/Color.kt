package app.binky.tracker.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// GENERATED — do not hand-edit individual roles.
//
// Both schemes are derived from four brand seeds by Material's tonal palette
// construction (CAM16/HCT), per the Phase 7 design brief: the seeds are the
// judgement, the ~36 roles are arithmetic. Hand-picking a role breaks the fixed
// tonal relationship it holds with the rest of its family, and the failure shows
// up as a contrast bug on one screen out of thirty rather than as a visible
// mistake here.
//
//   primary   #AE6A70  dusty rose  — brand: filled buttons, FAB, selected tab, chart line
//   secondary #93856A  hay         — quiet containers: chips, tonal buttons, selected segment
//   tertiary  #D98452  apricot     — caution only: the trend flag marker, the active watch
//   neutral   #E9E0D6  warm cream  — seeds every surface, so backgrounds read paper-warm
//
// Error stays M3 baseline (hue 25, chroma 84): a safety signal, not a brand choice.
//
// To change the palette, edit the seeds in scripts/gen_scheme.py and re-run it.
// ADR-0012 and ADR-0027: colours come from MaterialTheme, never literals, and
// dynamic colour defaults off so this file is what a user actually sees.

private val BinkyLightColors =
    lightColorScheme(
        primary = Color(0xFF8A4C52),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDADD),
        onPrimaryContainer = Color(0xFF380B12),
        inversePrimary = Color(0xFFFFB2B8),
        secondary = Color(0xFF695D45),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF2E1C2),
        onSecondaryContainer = Color(0xFF231A07),
        tertiary = Color(0xFF914B1E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBC7),
        onTertiaryContainer = Color(0xFF331100),
        error = Color(0xFFBA1B1B),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD4),
        onErrorContainer = Color(0xFF410001),
        background = Color(0xFFFFF8EF),
        onBackground = Color(0xFF1F1B15),
        surface = Color(0xFFFFF8EF),
        onSurface = Color(0xFF1F1B15),
        surfaceVariant = Color(0xFFEEE1CF),
        onSurfaceVariant = Color(0xFF4E4639),
        surfaceDim = Color(0xFFE2D9CF),
        surfaceBright = Color(0xFFFFF8EF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFBF2E8),
        surfaceContainer = Color(0xFFF6EDE3),
        surfaceContainerHigh = Color(0xFFF0E7DC),
        surfaceContainerHighest = Color(0xFFEAE1D7),
        inverseSurface = Color(0xFF353029),
        inverseOnSurface = Color(0xFFF8EFE5),
        outline = Color(0xFF807667),
        outlineVariant = Color(0xFFD1C5B4),
        scrim = Color(0xFF000000),
    )

private val BinkyDarkColors =
    darkColorScheme(
        primary = Color(0xFFFFB2B8),
        onPrimary = Color(0xFF531F26),
        primaryContainer = Color(0xFF6E363C),
        onPrimaryContainer = Color(0xFFFFDADD),
        inversePrimary = Color(0xFF8A4C52),
        secondary = Color(0xFFD6C5A7),
        onSecondary = Color(0xFF392F1A),
        secondaryContainer = Color(0xFF50452E),
        onSecondaryContainer = Color(0xFFF2E1C2),
        tertiary = Color(0xFFFFB68C),
        onTertiary = Color(0xFF542100),
        tertiaryContainer = Color(0xFF743507),
        onTertiaryContainer = Color(0xFFFFDBC7),
        error = Color(0xFFFFB4A9),
        onError = Color(0xFF680003),
        errorContainer = Color(0xFF930006),
        onErrorContainer = Color(0xFFFFDAD4),
        background = Color(0xFF16130D),
        onBackground = Color(0xFFEAE1D7),
        surface = Color(0xFF16130D),
        onSurface = Color(0xFFEAE1D7),
        surfaceVariant = Color(0xFF4E4639),
        onSurfaceVariant = Color(0xFFD1C5B4),
        surfaceDim = Color(0xFF16130D),
        surfaceBright = Color(0xFF3D3831),
        surfaceContainerLowest = Color(0xFF110E08),
        surfaceContainerLow = Color(0xFF1F1B15),
        surfaceContainer = Color(0xFF231F19),
        surfaceContainerHigh = Color(0xFF2D2923),
        surfaceContainerHighest = Color(0xFF39342D),
        inverseSurface = Color(0xFFEAE1D7),
        inverseOnSurface = Color(0xFF353029),
        outline = Color(0xFF9A8F80),
        outlineVariant = Color(0xFF4E4639),
        scrim = Color(0xFF000000),
    )

internal val LightColors = BinkyLightColors
internal val DarkColors = BinkyDarkColors
