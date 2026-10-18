package com.adlessarcade.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.adlessarcade.core.shop.ProfileState
import com.adlessarcade.core.shop.ShopCatalog
import com.adlessarcade.core.shop.ShopCategory

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

enum class CardBackStyle { GALAXY, SKULL, BRICKS, CAT }

/** Kartenrückseite in Galaxy Towers: Grundfarbe plus zwei Motivfarben (Planet/Ring, Fugen/Block, Fell/Augen …). */
@Immutable
data class CardBack(val base: Color, val accent: Color, val detail: Color, val style: CardBackStyle)

enum class BackdropStyle { STARS, NEBULA, ASTEROIDS, CORE }

/** Weltraum-Hintergrund in Galaxy Towers. */
@Immutable
data class SpaceBackdrop(val top: Color, val bottom: Color, val accent: Color, val style: BackdropStyle)

@Immutable
data class ArcadePalette(val accent: Color, val accent2: Color, val backgroundTop: Color) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accent2))
    val backgroundBrush: Brush
        get() = Brush.verticalGradient(listOf(backgroundTop, ArcadeBackground, ArcadeBackground))
}

/** Motiv einer Arena: bestimmt Kachel-Texturen, Rahmen, Requisiten und Umgebungs-Partikel. */
enum class ArenaStyle { DUSK, FOREST, CRYPT, FROST, LAVA }

/** Brett-Farben in Rune Tactics: gegnerische und eigene Hälfte jeweils hell/dunkel, Trennlinie, Rahmen und Motiv. */
@Immutable
data class ArenaTheme(
    val enemyDark: Color,
    val enemyLight: Color,
    val playerDark: Color,
    val playerLight: Color,
    val divider: Color,
    val frame: Color,
    val backdrop: Color,
    val style: ArenaStyle,
)

/** Aktuell ausgerüstete Kosmetik – per [LocalCosmetics] überall verfügbar. */
@Immutable
data class Cosmetics(
    val blockSkin: BlockSkin,
    val heroSkin: HeroSkin,
    val cardBack: CardBack,
    val backdrop: SpaceBackdrop,
    val arena: ArenaTheme,
    val palette: ArcadePalette,
    val avatar: String,
    val title: String,
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

    private val cardBacks = mapOf(
        // Wie im ersten Teil: farbige Karte mit lila Ringplanet und grauen Asteroiden, dazu Motiv-Rückseiten.
        "cardback_galaxy" to CardBack(Color(0xFF6D35A0), Color(0xFF9D3FBF), Color(0xFFF5A04A), CardBackStyle.GALAXY),
        "cardback_orange" to CardBack(Color(0xFFF7931E), Color(0xFF9D3FBF), Color(0xFFF5C26B), CardBackStyle.GALAXY),
        "cardback_red" to CardBack(Color(0xFFE8321E), Color(0xFF9D3FBF), Color(0xFFF5A04A), CardBackStyle.GALAXY),
        "cardback_blue" to CardBack(Color(0xFF0C47B0), Color(0xFF9D3FBF), Color(0xFFF5A04A), CardBackStyle.GALAXY),
        "cardback_black" to CardBack(Color(0xFF2A2A2E), Color(0xFF9D3FBF), Color(0xFFF5A04A), CardBackStyle.GALAXY),
        "cardback_skull" to CardBack(Color(0xFF2A2A2E), Color(0xFFF5F5F4), Color(0xFF111111), CardBackStyle.SKULL),
        "cardback_bricks" to CardBack(Color(0xFF9A4A1C), Color(0xFF5C2A0E), Color(0xFFF7A23B), CardBackStyle.BRICKS),
        "cardback_cat" to CardBack(Color(0xFF8A9BE0), Color(0xFF8C8279), Color(0xFF4A4038), CardBackStyle.CAT),
    )

    private val backdrops = mapOf(
        "backdrop_stars" to SpaceBackdrop(Color(0xFF000000), Color(0xFF000000), Color(0xFFE5E7EB), BackdropStyle.STARS),
        "backdrop_nebula" to SpaceBackdrop(Color(0xFF2A0F3D), Color(0xFF070414), Color(0xFFF472B6), BackdropStyle.NEBULA),
        "backdrop_asteroids" to SpaceBackdrop(Color(0xFF15171F), Color(0xFF07080C), Color(0xFFA8A29E), BackdropStyle.ASTEROIDS),
        "backdrop_core" to SpaceBackdrop(Color(0xFF1E1036), Color(0xFF05020D), Color(0xFFFDE68A), BackdropStyle.CORE),
    )

    private val arenas = mapOf(
        "arena_dusk" to ArenaTheme(Color(0xFF1C1520), Color(0xFF221A27), Color(0xFF111A24), Color(0xFF16212D), Color(0xFFFF5C6C), Color(0xFF3A3340), Color(0xFF0E0B14), ArenaStyle.DUSK),
        "arena_forest" to ArenaTheme(Color(0xFF33291A), Color(0xFF3B301F), Color(0xFF1C3A22), Color(0xFF224529), Color(0xFF86EFAC), Color(0xFF5B3A1E), Color(0xFF07140B), ArenaStyle.FOREST),
        "arena_crypt" to ArenaTheme(Color(0xFF1A1A1F), Color(0xFF222228), Color(0xFF16141F), Color(0xFF1D1A29), Color(0xFFA78BFA), Color(0xFF3F3F46), Color(0xFF0A0A0F), ArenaStyle.CRYPT),
        "arena_frost" to ArenaTheme(Color(0xFF162030), Color(0xFF1C293C), Color(0xFF14232B), Color(0xFF1A2E38), Color(0xFF7DD3FC), Color(0xFF94A3B8), Color(0xFF0B1424), ArenaStyle.FROST),
        "arena_lava" to ArenaTheme(Color(0xFF2A0F0C), Color(0xFF341410), Color(0xFF1C1210), Color(0xFF261813), Color(0xFFFB923C), Color(0xFF292524), Color(0xFF160605), ArenaStyle.LAVA),
    )

    private val palettes = mapOf(
        "theme_neon" to ArcadePalette(NeonViolet, NeonCyan, ArcadeBackgroundTop),
        "theme_ocean" to ArcadePalette(Color(0xFF2563EB), Color(0xFF2DD4BF), Color(0xFF0E1A2E)),
        "theme_sunset" to ArcadePalette(Color(0xFFFF6B35), Color(0xFFF472B6), Color(0xFF2A1420)),
        "theme_matrix" to ArcadePalette(Color(0xFF16A34A), Color(0xFF86EFAC), Color(0xFF0B1F12)),
        "theme_gold" to ArcadePalette(Color(0xFFB45309), Color(0xFFFACC15), Color(0xFF241A0B)),
    )

    fun blockSkin(id: String): BlockSkin = blockSkins[id] ?: blockSkins.getValue("block_classic")
    fun heroSkin(id: String): HeroSkin = heroSkins[id] ?: heroSkins.getValue("hero_default")
    fun palette(id: String): ArcadePalette = palettes[id] ?: palettes.getValue("theme_neon")
    fun cardBack(id: String): CardBack = cardBacks[id] ?: cardBacks.getValue("cardback_galaxy")
    fun backdrop(id: String): SpaceBackdrop = backdrops[id] ?: backdrops.getValue("backdrop_stars")
    fun arena(id: String): ArenaTheme = arenas[id] ?: arenas.getValue("arena_dusk")

    fun from(profile: ProfileState): Cosmetics = Cosmetics(
        blockSkin = blockSkin(profile.equipped(ShopCategory.BLOCK_SKIN).id),
        heroSkin = heroSkin(profile.equipped(ShopCategory.HERO_SKIN).id),
        cardBack = cardBack(profile.equipped(ShopCategory.CARD_BACK).id),
        backdrop = backdrop(profile.equipped(ShopCategory.SPACE_BACKDROP).id),
        arena = arena(profile.equipped(ShopCategory.ARENA).id),
        palette = palette(profile.equipped(ShopCategory.APP_THEME).id),
        avatar = profile.equipped(ShopCategory.AVATAR).symbol ?: "🎮",
        title = profile.equipped(ShopCategory.TITLE).name,
    )

    val Default: Cosmetics = from(ProfileState())

    /** Prüft beim Start, dass jeder Katalog-Artikel eine Optik hat (Tippfehler in IDs fallen so sofort auf). */
    fun missingVisuals(): List<String> = ShopCatalog.items.filter {
        when (it.category) {
            ShopCategory.BLOCK_SKIN -> it.id !in blockSkins
            ShopCategory.HERO_SKIN -> it.id !in heroSkins
            ShopCategory.CARD_BACK -> it.id !in cardBacks
            ShopCategory.SPACE_BACKDROP -> it.id !in backdrops
            ShopCategory.ARENA -> it.id !in arenas
            ShopCategory.APP_THEME -> it.id !in palettes
            ShopCategory.AVATAR -> it.symbol == null
            ShopCategory.TITLE -> false
        }
    }.map { it.id }
}

val LocalCosmetics = compositionLocalOf { CosmeticRegistry.Default }
