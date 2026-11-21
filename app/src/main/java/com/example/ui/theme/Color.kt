package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Precision Studio Obsidian Dark Palette
val ObsidianBg = Color(0xFF0A0F1A)
val ObsidianSurface = Color(0xFF111827)
val ObsidianSurfaceVariant = Color(0xFF1E293B)
val ObsidianOutline = Color(0xFF334155)

// Electric Emerald Vault (Primary Privacy & Action Accent)
val EmeraldPrimary = Color(0xFF10B981)
val EmeraldOnPrimary = Color(0xFF022C22)
val EmeraldContainer = Color(0xFF064E3B)
val EmeraldOnContainer = Color(0xFFA7F3D0)

// Warm Amber Waveform (Secondary Accent)
val AmberSecondary = Color(0xFFF59E0B)
val AmberOnSecondary = Color(0xFF2B1700)
val AmberContainer = Color(0xFF451A03)
val AmberOnContainer = Color(0xFFFDE68A)

// Sky Cyan Data / JSON Accent (Tertiary)
val CyanTertiary = Color(0xFF38BDF8)
val CyanOnTertiary = Color(0xFF082F49)
val CyanContainer = Color(0xFF0C4A6E)
val CyanOnContainer = Color(0xFFBAE6FD)

val ErrorDark = Color(0xFFF87171)
val OnErrorDark = Color(0xFF450A0A)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFECACA)

// Crisp Alabaster Studio Light Palette
val AlabasterBg = Color(0xFFF8FAFC)
val AlabasterSurface = Color(0xFFFFFFFF)
val AlabasterSurfaceVariant = Color(0xFFE2E8F0)
val AlabasterOutline = Color(0xFFCBD5E1)

val EmeraldPrimaryLight = Color(0xFF059669)
val EmeraldOnPrimaryLight = Color(0xFFFFFFFF)
val EmeraldContainerLight = Color(0xFFD1FAE5)
val EmeraldOnContainerLight = Color(0xFF064E3B)

val AmberSecondaryLight = Color(0xFFD97706)
val AmberOnSecondaryLight = Color(0xFFFFFFFF)
val AmberContainerLight = Color(0xFFFEF3C7)
val AmberOnContainerLight = Color(0xFF78350F)

val CyanTertiaryLight = Color(0xFF0284C7)
val CyanOnTertiaryLight = Color(0xFFFFFFFF)
val CyanContainerLight = Color(0xFFE0F2FE)
val CyanOnContainerLight = Color(0xFF0C4A6E)

val VaultDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = AmberSecondary,
    onSecondary = AmberOnSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = CyanTertiary,
    onTertiary = CyanOnTertiary,
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = CyanOnContainer,
    background = ObsidianBg,
    onBackground = Color(0xFFF1F5F9),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E293B),
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

val VaultLightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = AmberOnSecondaryLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = CyanTertiaryLight,
    onTertiary = CyanOnTertiaryLight,
    tertiaryContainer = CyanContainerLight,
    onTertiaryContainer = CyanOnContainerLight,
    background = AlabasterBg,
    onBackground = Color(0xFF0F172A),
    surface = AlabasterSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)
