package com.rescuemesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EmergencyColorScheme = lightColorScheme(
    primary = EmergencyTeal,
    secondary = EmergencyAmber,
    tertiary = EmergencyBlue,
    error = EmergencyRed,
    background = DeepDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun RescueMeshTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EmergencyColorScheme,
        content = content
    )
}
