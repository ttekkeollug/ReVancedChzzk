package com.seolha.rechzzk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF141517)
    val Surface = Color(0xFF1E2023)
    val Surface2 = Color(0xFF2A2C30)
    val Accent = Color(0xFF00F0A0)
    val Live = Color(0xFFE5202E)
    val Text = Color(0xFFF2F3F5)
    val Sub = Color(0xFF9DA1A8)
    val Dim = Color(0xFF6B6F76)

    /** 채팅 닉네임 색 (userIdHash로 고정 배정) */
    val nickColors = listOf(
        Color(0xFFEE7F55), Color(0xFF7FB7FF), Color(0xFFD38BFF), Color(0xFF6FD99B),
        Color(0xFFFF8FB1), Color(0xFFFFC857), Color(0xFF58D3E0), Color(0xFFB4E06A),
    )
    fun nick(hash: String) = nickColors[(hash.hashCode() and 0x7fffffff) % nickColors.size]
}

@Composable
fun ReChzzkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = Color.Black,
            background = C.Bg, surface = C.Bg, surfaceVariant = C.Surface,
            surfaceContainer = C.Surface, surfaceContainerHigh = C.Surface2, surfaceContainerLow = C.Surface,
            onBackground = C.Text, onSurface = C.Text, onSurfaceVariant = C.Sub,
            secondaryContainer = C.Surface2, onSecondaryContainer = C.Text,
        ),
        content = content,
    )
}
