package io.holyarrs.tv.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.holyarrs.tv.R

/**
 * Design tokens mirrored from HolyArrs' web app (lib/core/tokens.css).
 * The TV is read from a sofa, so the web's pixel sizes are scaled by 1.55, like the
 * Apple TV app. Android TV lays out at 960x540 dp, half the Apple TV's points.
 */
const val SCALE = 1.55f

/** Web pixels to dp on the TV. */
val Number.wp: Dp get() = (toFloat() * SCALE / 2f).dp

/** Apple TV points (1920x1080) to dp (960x540). */
val Number.pt: Dp get() = (toFloat() / 2f).dp

private fun c(hex: Long, alpha: Float = 1f) = Color(0xFF000000 or hex).copy(alpha = alpha)

class Palette(val dark: Boolean) {
    private fun d(light: Long, darkHex: Long, la: Float = 1f, da: Float = 1f) =
        if (dark) c(darkHex, da) else c(light, la)


    val bg = d(0xFFFFFF, 0x0B0B0C)
    val sidebarBG = d(0xF6F6F7, 0x111113)
    val surface1 = d(0xFFFFFF, 0x161618)
    val surface2 = d(0xF2F2F4, 0x1E1E21)
    val surface3 = d(0xE9E9EC, 0x2A2A2E)
    val line = d(0x000000, 0xFFFFFF, 0.08f, 0.09f)
    val lineStrong = d(0x000000, 0xFFFFFF, 0.16f, 0.18f)
    val text = d(0x1D1D1F, 0xF5F5F7)
    val textMuted = d(0x6E6E73, 0xA1A1A6)
    val textFaint = d(0x8E8E93, 0x7C7C80)
    val accent = d(0xE5352B, 0xFF453A)
    val accentText = d(0xCF2C23, 0xFF453A)
    val accentFill = c(0xD42D24)
    val accentSoft = d(0xE5352B, 0xFF453A, 0.10f, 0.16f)
    val ok = d(0x248A3D, 0x30D158)
    val wait = d(0xB25000, 0xFFB340)
    val fail = d(0xD70015, 0xFF6961)
    val info = d(0x0071E3, 0x409CFF)
    val segThumb = d(0xFFFFFF, 0x3A3A3E)
    /** The focus ring: white on dark, near-black on light (the web's focus outline). */
    val focus = if (dark) Color.White else c(0x1D1D1F)
}

object Marks {
    val have = c(0x1F9D48)
    val download = c(0x4F46E5)
    val ask = c(0xF0A020)
    val no = c(0xD92D20)
}

/** Tile icon colours on Home. */
object Tiles {
    val amber = c(0xF5A524)
    val orange = c(0xF97316)
    val indigo = c(0x6366F1)
    val teal = c(0x14B8A6)
    val cyan = c(0x22A6C3)
    val pink = c(0xF43F5E)
    val purple = c(0xA855F7)
}

object Radii {
    val sm = 6.wp
    val btn = 10.wp
    val card = 12.wp
    val panel = 16.wp
    val modal = 20.wp
}

object Space {
    val gutter = 64.pt
    val shelfGap = 16.wp
}

val LocalPalette = staticCompositionLocalOf { Palette(true) }

object Theme {
    val p: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current
}

@Composable
fun HolyTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides Palette(dark), content = content)
}

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Geist at the web type scale. */
object Typo {
    fun geist(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontFamily = Geist, fontWeight = weight, fontSize = (size * SCALE / 2f).sp, lineHeight = (size * SCALE / 2f * 1.25f).sp)

    val largeTitle = geist(34f, FontWeight.Bold)
    val title1 = geist(28f, FontWeight.Bold)
    val title2 = geist(22f, FontWeight.Bold)
    val title3 = geist(19f, FontWeight.SemiBold)
    val headline = geist(16f, FontWeight.SemiBold)
    val body = geist(15f)
    val callout = geist(14f)
    val subhead = geist(13f, FontWeight.Medium)
    val footnote = geist(12f, FontWeight.Medium)
    val caption = geist(11f, FontWeight.SemiBold)
}

val Float.wsp: TextUnit get() = (this * SCALE / 2f).sp

/** The web app draws a gradient with initials where a picture is missing. */
object Placeholder {
    private val palettes = listOf(
        0x5B4A32L to 0x3A3222L, 0x2F5F63L to 0x1F3540L, 0x5A2E55L to 0x3B1E33L, 0x2C4258L to 0x1F2A3DL,
        0x3E5B2CL to 0x24391FL, 0x5B3A2EL to 0x3A2620L, 0x46552FL to 0x2C3A1EL, 0x33365EL to 0x241F44L,
    )

    fun colors(name: String): List<Color> {
        var h = 5381u
        for (cp in name.codePoints().toArray()) h = h * 33u + cp.toUInt()
        val p = palettes[(h % palettes.size.toUInt()).toInt()]
        return listOf(c(p.first), c(p.second))
    }

    fun initials(name: String): String =
        name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.take(1) }.uppercase()
}
