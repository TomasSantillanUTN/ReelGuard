package com.example.pruebareel.data.preferences

data class AppSettings(
    val reelsEnabled: Boolean,
    val reelsLimit: Int,
    val shortsEnabled: Boolean,
    val shortsTimeMs: Long
)
