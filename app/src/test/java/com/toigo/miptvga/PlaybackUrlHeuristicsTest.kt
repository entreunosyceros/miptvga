package com.toigo.miptvga

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUrlHeuristicsTest {
    @Test
    fun explicitLiveQueryAndAmbiguousQueryHaveDifferentClassificationButKeepRecovery() {
        val ambiguous = "https://example.invalid/get.php?username=demo&password=synthetic"
        assertFalse(isLikelyLiveStream(ambiguous))
        assertTrue(shouldReconnectAfterEnd(ambiguous))
        assertFalse(shouldUsePlaybackCache(ambiguous))
        assertTrue(isLikelyLiveStream("$ambiguous&type=live"))
        assertTrue(isLikelyLiveStream("$ambiguous&type=m3u_plus"))
    }
    @Test
    fun detectsPlainTransportStreamAsLive() {
        val url = "https://stream.example.com/channel/main.ts?token=abc123"

        assertTrue(isLikelyLiveStream(url))
        assertEquals(MimeTypes.VIDEO_MP2T, inferMimeType(url))
    }

    @Test
    fun detectsXtreamLiveQueryPlaylistAsLive() {
        val url = "http://iptv.example.com/get.php?username=user&password=pass&type=m3u_plus"

        assertTrue(isLikelyLiveStream(url))
        assertTrue(shouldReconnectAfterEnd(url))
    }

    @Test
    fun detectsXtreamLiveOutputTsAsLive() {
        val url = "http://iptv.example.com/live/user/pass/12345.ts"

        assertTrue(isLikelyLiveStream(url))
        assertTrue(shouldReconnectAfterEnd(url))
        assertEquals(MimeTypes.VIDEO_MP2T, inferMimeType(url))
    }

    @Test
    fun keepsProgressiveVideoAsNonLive() {
        val url = "https://cdn.example.com/video/movie.mp4"

        assertFalse(isLikelyLiveStream(url))
        assertFalse(shouldReconnectAfterEnd(url))
    }

    @Test
    fun handlesTrimmedUppercaseTransportStreamUrls() {
        val url = "  HTTPS://STREAM.EXAMPLE.COM/CHANNEL/MAIN.TS?TOKEN=ABC123  "

        assertTrue(isLikelyLiveStream(url))
        assertTrue(shouldReconnectAfterEnd(url))
        assertEquals(MimeTypes.VIDEO_MP2T, inferMimeType(url))
    }

    @Test
    fun reconnectsAmbiguousRemoteXtreamStyleStreamAfterEnd() {
        val url = "https://iptv.example.com/get.php?username=user&password=pass"

        assertFalse(isLikelyLiveStream(url))
        assertTrue(shouldReconnectAfterEnd(url))
    }

    @Test
    fun doesNotReconnectLocalFilesAfterEnd() {
        val url = "file:///storage/emulated/0/Movies/demo.mp4"

        assertFalse(isLikelyLiveStream(url))
        assertFalse(shouldReconnectAfterEnd(url))
    }

    @Test
    fun keepsXtreamMovieStreamAsVod() {
        val url = "http://iptv.example.com/movie/user/pass/98765.mp4"

        assertFalse(isLikelyLiveStream(url))
        assertFalse(shouldReconnectAfterEnd(url))
        assertTrue(isXtreamVodStreamUrl(url))
    }

    @Test
    fun keepsXtreamSeriesActionAsVod() {
        val url = "http://iptv.example.com/player_api.php?username=user&password=pass&action=get_series_stream&stream=55"

        assertFalse(isLikelyLiveStream(url))
        assertFalse(shouldReconnectAfterEnd(url))
        assertTrue(isXtreamVodStreamUrl(url))
    }

    @Test
    fun reconnectsRemoteDashManifestAfterEnd() {
        val url = "https://stream.example.com/live/channel.mpd"

        assertFalse(isLikelyLiveStream(url))
        assertTrue(shouldReconnectAfterEnd(url))
        assertEquals(MimeTypes.APPLICATION_MPD, inferMimeType(url))
    }

    @Test
    fun recoversRemoteXtreamLikeStreamAfterEnd() {
        val url = "https://iptv.example.com/get.php?username=user&password=pass"

        assertFalse(isLikelyLiveStream(url))
        assertTrue(shouldReconnectAfterEnd(url))
        assertTrue(shouldApplyLivePlaybackHandling(url))
        assertTrue(shouldRecoverEndedPlayback(url))
    }

    @Test
    fun disablesPlaybackCacheForAmbiguousRemoteXtreamLikeStream() {
        val url = "https://iptv.example.com/get.php?username=user&password=pass"

        assertFalse(isLikelyLiveStream(url))
        assertFalse(shouldUsePlaybackCache(url))
    }

    @Test
    fun keepsProgressiveVodCacheDecisionUntouched() {
        val url = "https://cdn.example.com/catalog/trailer.mp4?token=abc"

        assertFalse(shouldApplyLivePlaybackHandling(url))
        assertFalse(shouldUsePlaybackCache(url))
    }

    @Test
    fun doesNotRecoverProgressiveVodAfterEnd() {
        val url = "https://cdn.example.com/catalog/trailer.mp4?token=abc"

        assertFalse(shouldRecoverEndedPlayback(url))
    }

    @Test
    fun doesNotRecoverLocalFileAfterEnd() {
        val url = "/storage/emulated/0/Movies/demo.mkv"

        assertFalse(shouldRecoverEndedPlayback(url))
    }
}
