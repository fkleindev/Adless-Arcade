package com.adlessarcade.core

import kotlinx.serialization.json.Json

/** Gemeinsames JSON-Format für alle Spielstände; tolerant gegenüber neuen/entfernten Feldern. */
val SaveJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
