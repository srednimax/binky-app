package app.binky.tracker.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.binky.tracker.R

// Nunito ships upstream only as a variable font — one file with a continuous
// `wght` axis rather than one file per weight. Android has supported those since
// API 26, which is exactly our minSdk, so we register the same resource twice at
// the two weights the scale actually uses and let the axis do the rest.
//
// Each weight is its own XML font resource that pins the `wght` axis, rather than
// Compose's FontVariation.Settings — that API is silently ignored for resource
// fonts, so the family rendered at Nunito's default instance (ExtraLight 200) and
// looked *thinner* than the Roboto it replaced, with no error anywhere.
private val Nunito =
    FontFamily(
        Font(R.font.nunito_bold, weight = FontWeight.Bold),
        Font(R.font.nunito_extrabold, weight = FontWeight.ExtraBold),
    )

// Nunito for display and headline only — its rounded terminals are where the
// softness comes from, and it is the single decorative decision in the app.
// Everything else stays FontFamily.Default, which is Roboto on Android.
//
// titleSmall, labelMedium and labelSmall are deliberately absent: the design
// leaves them at M3's defaults, and naming them here would freeze values that
// should keep tracking the Material baseline.
val Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 52.sp,
                lineHeight = 58.sp,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp,
                lineHeight = 50.sp,
                letterSpacing = (-0.3).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.2).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.2).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.2.sp,
            ),
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
    )
