package io.niko.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The Niko design system in Compose, for the screens drawn from the canvas (docs/ui/platforms.md; Compose is the
 * owner's choice after docs/research/ui-probe.md). Its values come from shared/tokens.json through the generated
 * NikoTokens.kt: [NikoColors] for the theme night mode asks for, [NikoType], [NikoSpace], [NikoRadius], [NikoSize],
 * [NikoStroke]. No Material: the screens draw the design system's own components from foundation.
 */
val LocalNikoColors = staticCompositionLocalOf { NikoColors.Light }

@Composable
fun NikoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNikoColors provides if (dark) NikoColors.Dark else NikoColors.Light, content = content)
}

/** The current theme's values: `Niko.colors.ink`. */
object Niko {
    val colors: NikoColors
        @Composable @ReadOnlyComposable get() = LocalNikoColors.current
}
