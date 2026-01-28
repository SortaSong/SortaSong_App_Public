package com.sortasong.sortasong

data class GameVerificationStatus(
    val game: GameEntry,
    val totalTracks: Int,
    val availableTracks: Int,
    val unavailableTracks: Int,
    val notVerifiedTracks: Int
) {
    val statusText: String
        get() = when {
            notVerifiedTracks > 0 -> "Wird überprüft... ($availableTracks/$totalTracks)"
            unavailableTracks == 0 -> "✓ Alle verfügbar ($totalTracks)"
            else -> "⚠ $unavailableTracks fehlen"
        }

    val isClickable: Boolean
        get() = unavailableTracks > 0
}
