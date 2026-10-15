package com.adlessarcade.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlessarcade.arcadeApp
import com.adlessarcade.core.GameRegistry
import com.adlessarcade.core.achievements.Achievement
import com.adlessarcade.core.achievements.Achievements
import com.adlessarcade.core.shop.ProfileState
import com.adlessarcade.core.shop.ShopCatalog
import com.adlessarcade.core.shop.ShopCategory
import com.adlessarcade.ui.components.ArcadeScaffold
import com.adlessarcade.ui.theme.ArcadeOutline
import com.adlessarcade.ui.theme.ArcadeSurface
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeText
import com.adlessarcade.ui.theme.ArcadeTextMuted
import com.adlessarcade.ui.theme.LocalCosmetics

private val Gold = Color(0xFFFACC15)

/** Alle Erfolge, nach Spiel gruppiert. Freigeschaltete Titel lassen sich hier direkt ausrüsten. */
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val repository = LocalContext.current.arcadeApp.profile
    val profile by repository.state.collectAsState(initial = ProfileState())
    val done = Achievements.all.count { profile.hasAchievement(it) }
    val equippedTitle = profile.equipped(ShopCategory.TITLE).id
    val groups = Achievements.all.groupBy { it.gameId }

    ArcadeScaffold(
        title = "Erfolge",
        subtitle = "$done von ${Achievements.all.size} geschafft",
        onBack = onBack,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Jeder Erfolg schaltet einen exklusiven Titel frei, den es nicht im Shop zu kaufen gibt. " +
                        "Tippe auf einen freigeschalteten Erfolg, um seinen Titel zu tragen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcadeTextMuted,
                )
            }
            groups.forEach { (gameId, achievements) ->
                item(key = "header_$gameId") {
                    Text(
                        (gameId?.let { GameRegistry.find(it)?.title } ?: "Spielübergreifend").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcadeTextMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(achievements, key = { it.id }) { achievement ->
                    val unlocked = profile.hasAchievement(achievement)
                    AchievementRow(
                        achievement = achievement,
                        unlocked = unlocked,
                        equipped = unlocked && equippedTitle == achievement.titleId,
                        onEquip = { ShopCatalog.find(achievement.titleId)?.let(repository::equip) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, unlocked: Boolean, equipped: Boolean, onEquip: () -> Unit) {
    val palette = LocalCosmetics.current.palette
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArcadeSurface)
            .border(
                if (equipped) 2.dp else 1.dp,
                when {
                    equipped -> palette.accent2
                    unlocked -> Gold.copy(alpha = 0.7f)
                    else -> ArcadeOutline
                },
                shape,
            )
            .clickable(enabled = unlocked && !equipped, onClick = onEquip)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (unlocked) Gold.copy(alpha = 0.18f) else ArcadeSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (unlocked) {
                Text(achievement.icon, fontSize = 24.sp)
            } else {
                Text(achievement.icon, fontSize = 24.sp, modifier = Modifier.alpha(0.25f))
                Icon(Icons.Filled.Lock, contentDescription = "Gesperrt", tint = ArcadeTextMuted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(achievement.name, style = MaterialTheme.typography.titleMedium, color = if (unlocked) ArcadeText else ArcadeTextMuted)
            Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = ArcadeTextMuted)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Titel: ", style = MaterialTheme.typography.labelLarge, color = ArcadeTextMuted)
                Text(achievement.titleName, style = MaterialTheme.typography.labelLarge, color = if (unlocked) Gold else ArcadeTextMuted)
            }
        }
        when {
            equipped -> Icon(Icons.Filled.Check, contentDescription = "Getragen", tint = palette.accent2, modifier = Modifier.size(20.dp))
            unlocked -> Text("Tragen", style = MaterialTheme.typography.labelLarge, color = ArcadeText)
        }
    }
}
