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
  surfaceVariant = Color(0xFF2C2C2E),
  secondaryContainer = Color(0xFF183B66),
)

private val LightColorScheme = lightColorScheme(
  primary = WhaleBlue,
  background = GroupedBackground,
  surface = GroupedSurface,
  surfaceVariant = Color(0xFFE9EBEF),
  secondaryContainer = WhaleBlueLight,
  onBackground = Color(0xFF111111),
  onSurface = Color(0xFF111111),
)

@Composable
fun WhaleHarnessTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
