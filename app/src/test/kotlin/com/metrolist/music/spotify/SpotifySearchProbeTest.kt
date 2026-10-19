package com.metrolist.music.spotify

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network diagnostic; deterministic matching tests remain the release gate. */
class SpotifySearchProbeTest {
    @Test fun inspectReportedTitlesAgainstCurrentYouTubeResults() = runBlocking {
        assumeTrue(System.getenv("SONET_MATCH_PROBE") == "1")
        val examples = listOf(
            SpotifyTrack("Dead", listOf("Floats", "Flowerbabe")),
            SpotifyTrack("On My Way", listOf("The Mirrors")),
            SpotifyTrack("Hurt", listOf("Vandal Moon")),
            SpotifyTrack("klk (feat. S1rena)", listOf("Mietze Conte", "S1RENA")),
        )
        val report = File("build/reports/tests/spotify-match-probe.txt")
        report.parentFile.mkdirs()
        report.writeText("Live diagnostic using reported titles/artists; Spotify durations were not supplied. Network failures do not indicate unavailable recordings.\n")
        for (track in examples) {
            val result = withTimeoutOrNull(15_000) {
                runCatching { SpotifyTrackResolver().resolve(track) }
                    .fold(onSuccess = { song -> song?.let { "${it.id} | ${it.title} | ${it.artists.joinToString { a -> a.name }} | ${it.duration}" } ?: "PENDING" },
                        onFailure = { "NETWORK_OR_RESPONSE: ${it.javaClass.simpleName}" })
            } ?: "TIMEOUT"
            report.appendText("${track.title} — ${track.artists.joinToString()} => $result\n")
        }
    }
}
