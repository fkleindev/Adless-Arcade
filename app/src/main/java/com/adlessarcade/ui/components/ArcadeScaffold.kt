package com.adlessarcade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.adlessarcade.ui.theme.ArcadeBackgroundBrush
import com.adlessarcade.ui.theme.ArcadeSurfaceHigh
import com.adlessarcade.ui.theme.ArcadeTextMuted

/** Einheitlicher Bildschirmrahmen: Verlaufshintergrund, Systemleisten-Abstand, optionale Top-Bar. */
@Composable
fun ArcadeScaffold(
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ArcadeBackgroundBrush)
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (title != null || onBack != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        ArcadeIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcadeTextMuted,
                            )
                        }
                    }
                    actions()
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), content = content)
        }
    }
}

@Composable
fun ArcadeIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ArcadeSurfaceHigh),
        content = content,
    )
}
