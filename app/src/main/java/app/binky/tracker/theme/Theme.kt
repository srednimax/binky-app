package app.binky.tracker.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Binky's theme.
 *
 * [dynamicColor] defaults to **false** per ADR-0027: Binky owns its palette and
 * Material You is opt-in. This is not a style preference — with dynamic colour on,
 * nothing on Android 12+ reads [LightColors] or [DarkColors] at all, so ADR-0012's
 * promise that "the visual pass then edits one file" would be false on almost every
 * device. The brand is only visible because this defaults off.
 *
 * The Settings toggle that lets a user turn Material You back on passes `true` here.
 */
@Composable
fun BinkyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            // Wallpaper-derived schemes exist only on Android 12+; below that the
            // opt-in silently has nothing to opt into, and the brand applies.
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
