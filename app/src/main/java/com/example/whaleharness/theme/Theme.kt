package com.example.whaleharness.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = DarkWhaleBlue,
  background = DarkBackground,
  surface = DarkSurface,
  surfaceVariant = Color(0xFF172448),
  secondaryContainer = Color(0xFF102D61),
  onBackground = Color(0xFFF4F7FF),
  onSurface = Color(0xFFF4F7FF),
  onSurfaceVariant = Color(0xFFB7C6E8),
)

private val LightColorScheme = lightColorScheme(
  primary = WhaleBlue,
  background = GroupedBackground,
  surface = GroupedSurface,
  surfaceVariant = Color(0xFFE8EFFB),
  secondaryContainer = WhaleBlueLight,
  onBackground = Color(0xFF0A1633),
  onSurface = Color(0xFF0A1633),
  onSurfaceVariant = Color(0xFF63708A),
)

@Composable
fun WhaleHarnessTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
