package com.example.nexusremote.domain.model

/** An installed TV app, identified by its validated package name. */
data class TvApp(
    val packageName: String,
    val label: String,
)
