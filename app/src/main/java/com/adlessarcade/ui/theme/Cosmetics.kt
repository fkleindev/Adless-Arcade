package com.adlessarcade.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.adlessarcade.core.shop.ProfileState
import com.adlessarcade.core.shop.ShopCatalog
import com.adlessarcade.core.shop.ShopCategory
import com.adlessarcade.games.survivors.HeroClass

enum class BlockStyle { BEVEL, FLAT, OUTLINE, GEM }

@Immutable
data class BlockSkin(val colors: List<Color>, val style: BlockStyle) {
    /** Farbe für Spielfeldwert 1..n. */
    fun color(value: Int): Color = colors[(value - 1).mod(colors.size)]
}

@Immutable
data class HeroSkin(
    val body: Color,
    val head: Color,
    val visor: Color,
    val bullet: Color,
    /** Projektile wechseln die Farbe über den Regenbogen. */
    val rainbow: Boolean = false,
)

@Immutable
data class ArcadePalette(val accent: Color, val accent2: Color, val backgroundTop: Color) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accent2))
    val backgroundBrush: Brush
        get() = Brush.verticalGradient(listOf(backgroundTop, ArcadeBackground, ArcadeBackground))
}

enum class DecorStyle { MEADOW, FROST, VOLCANO, GRAVEYARD, NEON }

/** Optik einer Survivors-Welt: Boden, Deko und Einfärbung der Gegner. */
@Immutable
data class SurvivorTheme(
    val ground: Color,
    val groundAlt: Color,
    val detail: Color,
    val accent: Color,
    val decor: DecorStyle,
    val enemyTint: Color = Color.Transparent,
    val tintAmount: Float = 0f,
    val vignette: Color = Color.Black,
)

/** Aktuell ausgerüstete Kosmetik – per [LocalCosmetics] überall verfügbar. */
@Immutable
data class Cosmetics(
    val blockSkin: BlockSkin,
    val heroSkin: HeroSkin,
    val palette: ArcadePalette,
    val avatar: String,
    val title: String,
    val survivorTheme: SurvivorTheme,
)

object CosmeticRegistry {
    private val blockSkins = mapOf(
        "block_classic" to BlockSkin(BlockColors, BlockStyle.BEVEL),
        "block_pastel" to BlockSkin(
            listOf(0xFFFFADAD, 0xFFFFD6A5, 0xFFFDFFB6, 0xFFCAFFBF, 0xFF9BF6FF, 0xFFA0C4FF, 0xFFBDB2FF).map { Color(it) },
            BlockStyle.FLAT,
        ),
        "block_neon" to BlockSkin(
            listOf(0xFFFF2E63, 0xFFFF8C00, 0xFFFFF000, 0xFF39FF14, 0xFF00F0FF, 0xFF2979FF, 0xFFD500F9).map { Color(it) },
            BlockStyle.OUTLINE,
        ),
        "block_ice" to BlockSkin(
            listOf(0xFFE0F7FF, 0xFFB3E5FC, 0xFF81D4FA, 0xFF4FC3F7, 0xFF29B6F6, 0xFF80DEEA, 0xFFB2EBF2).map { Color(it) },
            BlockStyle.BEVEL,
        ),
        "block_gem" to BlockSkin(
            listOf(0xFFE0115F, 0xFFFF7F00, 0xFFFFC87C, 0xFF50C878, 0xFF7FFFD4, 0xFF0F52BA, 0xFF9966CC).map { Color(it) },
            BlockStyle.GEM,
        ),
        "block_gold" to BlockSkin(
            listOf(0xFFFFD700, 0xFFF5C542, 0xFFE6B422, 0xFFDAA520, 0xFFFFE066, 0xFFC9A227, 0xFFFFC107).map { Color(it) },
            BlockStyle.GEM,
        ),
    )

    private val heroSkins = mapOf(
        "hero_default" to HeroSkin(NeonCyan, Color.White, NeonViolet, NeonCyan),
        "hero_ninja" to HeroSkin(Color(0xFF1F2230), Color(0xFF2A2E40), Color(0xFFFF3B5C), Color(0xFFFF3B5C)),
        "hero_toxic" to HeroSkin(Color(0xFF4ADE80), Color(0xFFD9F99D), Color(0xFF14532D), Color(0xFFA3E635)),
        "hero_plasma" to HeroSkin(NeonPink, Color.White, Color(0xFF7C3AED), Color(0xFFF472B6)),
        "hero_gold" to HeroSkin(Color(0xFFFACC15), Color(0xFFFFF7D6), Color(0xFFB45309), Color(0xFFFDE047)),
        "hero_rainbow" to HeroSkin(Color.White, Color.White, NeonViolet, Color.White, rainbow = true),
    )

    private val palettes = mapOf(
        "theme_neon" to ArcadePalette(NeonViolet, NeonCyan, ArcadeBackgroundTop),
        "theme_ocean" to ArcadePalette(Color(0xFF2563EB), Color(0xFF2DD4BF), Color(0xFF0E1A2E)),
        "theme_sunset" to ArcadePalette(Color(0xFFFF6B35), Color(0xFFF472B6), Color(0xFF2A1420)),
        "theme_matrix" to ArcadePalette(Color(0xFF16A34A), Color(0xFF86EFAC), Color(0xFF0B1F12)),
        "theme_gold" to ArcadePalette(Color(0xFFB45309), Color(0xFFFACC15), Color(0xFF241A0B)),
    )

    private val survivorThemes = mapOf(
        "world_meadow" to SurvivorTheme(
            ground = Color(0xFF1F3B24), groundAlt = Color(0xFF244530), detail = Color(0xFF4ADE80),
            accent = Color(0xFFFDE047), decor = DecorStyle.MEADOW,
        ),
        "world_frost" to SurvivorTheme(
            ground = Color(0xFF1E3A52), groundAlt = Color(0xFF23445F), detail = Color(0xFFBAE6FD),
            accent = Color(0xFFE0F2FE), decor = DecorStyle.FROST,
            enemyTint = Color(0xFF93C5FD), tintAmount = 0.25f, vignette = Color(0xFF0C1A2B),
        ),
        "world_volcano" to SurvivorTheme(
            ground = Color(0xFF2A1410), groundAlt = Color(0xFF331A13), detail = Color(0xFFF97316),
            accent = Color(0xFFFACC15), decor = DecorStyle.VOLCANO,
            enemyTint = Color(0xFFFF5C3A), tintAmount = 0.2f, vignette = Color(0xFF2A0500),
        ),
        "world_graveyard" to SurvivorTheme(
            ground = Color(0xFF1C1B26), groundAlt = Color(0xFF232131), detail = Color(0xFF6B7280),
            accent = Color(0xFFA78BFA), decor = DecorStyle.GRAVEYARD,
            enemyTint = Color(0xFF9CA3AF), tintAmount = 0.3f, vignette = Color(0xFF0A0A12),
        ),
        "world_neon" to SurvivorTheme(
            ground = Color(0xFF0B0B1A), groundAlt = Color(0xFF111129), detail = Color(0xFF22D3EE),
            accent = Color(0xFFF472B6), decor = DecorStyle.NEON,
            enemyTint = Color(0xFFE879F9), tintAmount = 0.3f, vignette = Color(0xFF05010F),
        ),
    )

    fun blockSkin(id: String): BlockSkin = blockSkins[id] ?: blockSkins.getValue("block_classic")
    fun heroSkin(id: String): HeroSkin = heroSkins[id] ?: heroSkins.getValue("hero_default")
    fun palette(id: String): ArcadePalette = palettes[id] ?: palettes.getValue("theme_neon")
    fun survivorTheme(id: String): SurvivorTheme = survivorThemes[id] ?: survivorThemes.getValue("world_meadow")

    fun from(profile: ProfileState): Cosmetics = Cosmetics(
        blockSkin = blockSkin(profile.equipped(ShopCategory.BLOCK_SKIN).id),
        heroSkin = heroSkin(profile.equipped(ShopCategory.HERO_SKIN).id),
        palette = palette(profile.equipped(ShopCategory.APP_THEME).id),
        avatar = profile.equipped(ShopCategory.AVATAR).symbol ?: "🎮",
        title = profile.equipped(ShopCategory.TITLE).name,
        survivorTheme = survivorTheme(profile.equipped(ShopCategory.SURVIVOR_THEME).id),
    )

    val Default: Cosmetics = from(ProfileState())

    /** Prüft beim Start, dass jeder Katalog-Artikel eine Optik hat (Tippfehler in IDs fallen so sofort auf). */
    fun missingVisuals(): List<String> = ShopCatalog.items.filter {
        when (it.category) {
            ShopCategory.BLOCK_SKIN -> it.id !in blockSkins
            ShopCategory.HERO_SKIN -> it.id !in heroSkins
            ShopCategory.SURVIVOR_CLASS -> HeroClass.fromShopId(it.id) == null
            ShopCategory.SURVIVOR_THEME -> it.id !in survivorThemes
            ShopCategory.APP_THEME -> it.id !in palettes
            ShopCategory.AVATAR -> it.symbol == null
            ShopCategory.TITLE -> false
        }
    }.map { it.id }
}

val LocalCosmetics = compositionLocalOf { CosmeticRegistry.Default }
