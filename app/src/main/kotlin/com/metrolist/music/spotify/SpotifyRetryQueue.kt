package com.metrolist.music.spotify

internal fun pendingSpotifyTracks(attempted: List<SpotifyTrack>, progress: SpotifyImportProgress): List<SpotifyTrack> =
    progress.remainingTracks ?: (progress.unmatchedTracks + attempted.drop(progress.processed.coerceIn(0, attempted.size))).distinct()
