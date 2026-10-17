package com.adlessarcade.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Erzwingt für die Dauer dieses Screens eine Ausrichtung (z. B. Querformat für Galaxy Towers 2)
 * und stellt beim Verlassen die vorherige wieder her. Die App ist sonst im Hochformat.
 */
@Composable
fun LockOrientation(orientation: Int) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, orientation) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose { activity.requestedOrientation = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
