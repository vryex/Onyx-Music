package com.metrolist.music.spotify

import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime

internal fun SongEntity.withSpotifyImportMembership(now: LocalDateTime): SongEntity =
    copy(liked = true, likedDate = likedDate ?: now, inLibrary = inLibrary ?: now)
