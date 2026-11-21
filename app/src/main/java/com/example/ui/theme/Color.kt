package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian Grimoire — Dark Palette
val DarkBackground = Color(0xFF0B0C12)
val DarkSurface = Color(0xFF12141E)
val DarkSurfaceVariant = Color(0xFF1B1E2C)
val DarkSurfaceElevated = Color(0xFF23273A)
val DarkOutline = Color(0xFF31364F)
val DarkOutlineVariant = Color(0xFF23273A)

val DarkPrimary = Color(0xFFB59CFF)
val DarkOnPrimary = Color(0xFF1B103D)
val DarkPrimaryContainer = Color(0xFF332466)
val DarkOnPrimaryContainer = Color(0xFFE9E0FF)

val DarkSecondary = Color(0xFFF5B041)
val DarkOnSecondary = Color(0xFF2C1900)
val DarkSecondaryContainer = Color(0xFF472D05)
val DarkOnSecondaryContainer = Color(0xFFFFDFA8)

val DarkTertiary = Color(0xFF5EEAD4)
val DarkOnTertiary = Color(0xFF002923)
val DarkTertiaryContainer = Color(0xFF0D443C)
val DarkOnTertiaryContainer = Color(0xFFB8FFF2)

val DarkOnBackground = Color(0xFFEDEFF7)
val DarkOnSurface = Color(0xFFEDEFF7)
val DarkOnSurfaceVariant = Color(0xFFA2A8C2)

val DarkError = Color(0xFFFF7A85)
val DarkOnError = Color(0xFF3E000A)
val DarkErrorContainer = Color(0xFF5C111B)
val DarkOnErrorContainer = Color(0xFFFFDAD9)

// Editorial Parchment — Light Palette
val LightBackground = Color(0xFFF8F6F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEEBE4)
val LightSurfaceElevated = Color(0xFFF4F1EB)
val LightOutline = Color(0xFFD5CFC4)
val LightOutlineVariant = Color(0xFFE7E2D8)

val LightPrimary = Color(0xFF5338C4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE9E2FF)
val LightOnPrimaryContainer = Color(0xFF1E0E63)

val LightSecondary = Color(0xFFB45309)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFEF0D6)
val LightOnSecondaryContainer = Color(0xFF421C00)

val LightTertiary = Color(0xFF0F766E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCFBF1)
val LightOnTertiaryContainer = Color(0xFF042F2E)

val LightOnBackground = Color(0xFF181922)
val LightOnSurface = Color(0xFF181922)
val LightOnSurfaceVariant = Color(0xFF595D70)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Semantic Roleplay Tokens
val DiceGoldDark = Color(0xFFFBBF24)
val DiceGoldLight = Color(0xFFD97706)
val OocTintDark = Color(0xFF1E293B)
val OocTintLight = Color(0xFFE2E8F0)
val SyncGreen = Color(0xFF34D399)

// Semantic Multi-Agent Entity Markup Colors (Dark & Light)
val SemanticLocDark = Color(0xFF34D399)
val SemanticLocLight = Color(0xFF047857)
val SemanticNpcDark = Color(0xFFF59E0B)
val SemanticNpcLight = Color(0xFFB45309)
val SemanticItemDark = Color(0xFF60A5FA)
val SemanticItemLight = Color(0xFF1D4ED8)
val SemanticQuestDark = Color(0xFFC084FC)
val SemanticQuestLight = Color(0xFF6D28D9)
val SemanticCheckDark = Color(0xFFFB7185)
val SemanticCheckLight = Color(0xFFBE123C)
val SemanticLoreDark = Color(0xFF2DD4BF)
val SemanticLoreLight = Color(0xFF0F766E)
val SemanticThoughtDark = Color(0xFFA5B4FC)
val SemanticThoughtLight = Color(0xFF4338CA)

fun semanticEntityColor(
    type: com.example.data.model.SemanticEntityType,
    isDark: Boolean
): Color = when (type) {
    com.example.data.model.SemanticEntityType.LOCATION -> if (isDark) SemanticLocDark else SemanticLocLight
    com.example.data.model.SemanticEntityType.NPC -> if (isDark) SemanticNpcDark else SemanticNpcLight
    com.example.data.model.SemanticEntityType.ITEM -> if (isDark) SemanticItemDark else SemanticItemLight
    com.example.data.model.SemanticEntityType.QUEST -> if (isDark) SemanticQuestDark else SemanticQuestLight
    com.example.data.model.SemanticEntityType.CHECK -> if (isDark) SemanticCheckDark else SemanticCheckLight
    com.example.data.model.SemanticEntityType.LORE -> if (isDark) SemanticLoreDark else SemanticLoreLight
    com.example.data.model.SemanticEntityType.THOUGHT -> if (isDark) SemanticThoughtDark else SemanticThoughtLight
}
