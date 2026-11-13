package com.karim.aiprojectmanager

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

internal val FilledActionBlue = Color(0xFF0D6EFD)

/** Explicit containers prevent baseline purple/tinted surfaces. Shared role colors keep every existing screen coherent in all appearance modes. */
internal fun appearanceColors(dark: Boolean): ColorScheme {
    val background = Color(if (dark) 0xFF0B101D else 0xFFF6F8FB)
    val card = Color(if (dark) 0xFF172131 else 0xFFFFFFFF)
    val text = Color(if (dark) 0xFFFFFFFF else 0xFF17263D)
    val primary = Color(if (dark) 0xFF00C2FF else 0xFF0B5ED7)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = if (dark) background else Color.White,
        primaryContainer = FilledActionBlue, onPrimaryContainer = Color.White,
        secondary = Color(if (dark) 0xFF00C2FF else 0xFF536276),
        onSecondary = if (dark) background else Color.White,
        secondaryContainer = Color(if (dark) 0xFF153946 else 0xFFE2EAF4),
        onSecondaryContainer = if (dark) Color.White else text,
        tertiary = Color(if (dark) 0xFF00C2FF else 0xFF006B8F),
        onTertiary = if (dark) background else Color.White,
        tertiaryContainer = Color(if (dark) 0xFF153946 else 0xFFE2EAF4),
        onTertiaryContainer = if (dark) Color.White else text,
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = card, onSurfaceVariant = Color(if (dark) 0xFFC4CCD6 else 0xFF536276),
        surfaceTint = Color.Transparent,
        surfaceDim = background, surfaceBright = card,
        surfaceContainerLowest = background,
        surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = card, surfaceContainerHighest = card,
        outline = Color(if (dark) 0xFF64748B else 0xFF788597), outlineVariant = Color(if (dark) 0xFF354258 else 0xFFD8E0EB),
        error = Color(if (dark) 0xFFFFB4AB else 0xFFB3261E),
        onError = Color(if (dark) 0xFF690005 else 0xFFFFFFFF),
        errorContainer = Color(if (dark) 0xFF93000A else 0xFFF9DEDC),
        onErrorContainer = Color(if (dark) 0xFFFFDAD6 else 0xFF410E0B),
        inverseSurface = if (dark) Color.White else Color(0xFF17263D),
        inverseOnSurface = if (dark) Color(0xFF17263D) else Color.White,
        inversePrimary = Color(if (dark) 0xFF0B5ED7 else 0xFF00C2FF), scrim = Color.Black
    )
}

@Composable
internal fun AppTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = appearanceColors(dark), typography = ProjectTypography, content = content)
}

/** Black interaction layers darken blue, retaining white-content contrast instead of lightening it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilledActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(color = Color.Black)) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled,
            shape = ProjectCardShape, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f)),
            colors = ButtonDefaults.buttonColors(containerColor = FilledActionBlue, contentColor = Color.White),
            content = content)
    }
}

/** API 35 uses existing enforced edge-to-edge; earlier APIs retain their existing inset policy. */
@Suppress("DEPRECATION")
internal fun updateWindowAppearance(activity: Activity, dark: Boolean) {
    val background = appearanceColors(dark).background.toArgb()
    activity.window.setBackgroundDrawable(ColorDrawable(background))
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
    if (Build.VERSION.SDK_INT < 35) {
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = background
    }
    // Do not disable navigation-bar contrast enforcement or change decor fitting/IME insets.
}
