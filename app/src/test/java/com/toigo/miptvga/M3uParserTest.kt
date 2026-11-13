package com.toigo.miptvga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun preservesCommasInNameAndQuotedAttributes() {
        val channels = parser.parse("#EXTINF:-1 group-title=\"Noticias, mundo\" tvg-logo=\"https://example.invalid/a,b.png\",Noticias, España\nhttps://example.invalid/live\n")
        assertEquals("Noticias, España", channels.single().name)
        assertEquals("Noticias, mundo", channels.single().group)
        assertEquals("https://example.invalid/a,b.png", channels.single().logoUrl)
    }

    @Test
    fun handlesBomBlankNameAndIncompleteEntry() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("#EXTINF:-1,No URL\n").isEmpty())
        val channel = parser.parse("\uFEFF#EXTM3U\n#EXTINF:-1,\nhttps://example.invalid/live\n").single()
        assertEquals("Canal sin nombre", channel.name)
    }

    @Test
    fun cancelsIncrementalReadWithoutConsumingWholeInput() {
        var readLines = 0
        val reader = object : java.io.BufferedReader(java.io.StringReader("")) {
            override fun readLine(): String {
                readLines++
                return if (readLines % 2 == 1) "#EXTINF:-1,Channel" else "https://example.invalid/live"
            }
        }
        var checks = 0
        try {
            parser.parse(reader) {
                if (++checks == 3) throw java.util.concurrent.CancellationException()
            }
            org.junit.Assert.fail("Cancellation must escape the parser")
        } catch (_: java.util.concurrent.CancellationException) {
            assertEquals(512, readLines)
        }
    }

    @Test
    fun parsesBasicExtInfEntries() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Noticias",Canal Uno
            http://example.com/stream1.m3u8
            #EXTINF:-1,Canal Dos
            http://example.com/stream2.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(2, channels.size)
        assertEquals("Canal Uno", channels[0].name)
        assertEquals("Noticias", channels[0].group)
        assertEquals("http://example.com/stream2.m3u8", channels[1].streamUrl)
    }

    @Test
    fun parsesChannelLogoAttribute() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://cdn.example.com/logo.png" group-title="Deportes",Canal Logo
            http://example.com/logo.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://cdn.example.com/logo.png", channels[0].logoUrl)
        assertEquals("Deportes", channels[0].group)
    }

    @Test
    fun parsesTvgIdAttribute() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="channel.one" group-title="General",Canal EPG
            http://example.com/epg.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("channel.one", channels[0].tvgId)
        assertEquals("General", channels[0].group)
    }

    @Test
    fun parsesProgressiveVideoEntries() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Películas",Vídeo demo
            https://example.com/video/movie.mp4
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://example.com/video/movie.mp4", channels[0].playbackUrl)
        assertTrue(channels[0].requestHeaders.isEmpty())
    }

    @Test
    fun parsesHeadersEmbeddedInPlaybackUrl() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Vídeo con cabeceras
            https://example.com/video.mp4|User-Agent=VLC/3.0|Referer=https://example.com/
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://example.com/video.mp4", channels[0].playbackUrl)
        assertEquals("VLC/3.0", channels[0].requestHeaders["User-Agent"])
        assertEquals("https://example.com/", channels[0].requestHeaders["Referer"])
    }

    @Test
    fun inlineHeadersOverridePreviousVlcOptions() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Vídeo con prioridad inline
            #EXTVLCOPT:http-user-agent=Kodi
            https://example.com/video.mp4|User-Agent=VLC/3.0
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://example.com/video.mp4", channels[0].playbackUrl)
        assertEquals("VLC/3.0", channels[0].requestHeaders["User-Agent"])
    }

    @Test
    fun parsesExtVlcOptHeaders() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Series",Capítulo 1
            #EXTVLCOPT:http-user-agent=Kodi
            #EXTVLCOPT:http-referrer=https://example.com/
            https://cdn.example.com/vod/episode01.mkv
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://cdn.example.com/vod/episode01.mkv", channels[0].playbackUrl)
        assertEquals("Kodi", channels[0].requestHeaders["User-Agent"])
        assertEquals("https://example.com/", channels[0].requestHeaders["Referer"])
    }

    @Test
    fun keepsUrlsWithNonHeaderPipesUntouched() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Canal firmado
            https://example.com/live/stream.m3u8|token|segment
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://example.com/live/stream.m3u8|token|segment", channels[0].playbackUrl)
        assertTrue(channels[0].requestHeaders.isEmpty())
    }

    @Test
    fun keepsUrlUntouchedWhenAnyInlineHeaderSegmentIsMalformed() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Canal con firma parcial
            https://example.com/live/stream.m3u8|User-Agent=VLC/3.0|token
        """.trimIndent()

        val channels = parser.parse(m3u)

        assertEquals(1, channels.size)
        assertEquals("https://example.com/live/stream.m3u8|User-Agent=VLC/3.0|token", channels[0].playbackUrl)
        assertTrue(channels[0].requestHeaders.isEmpty())
    }

    @Test
    fun parsesLargePlaylistQuicklyEnough() {
        val builder = StringBuilder("#EXTM3U\n")
        repeat(22000) { i ->
            builder.append("#EXTINF:-1,Canal ").append(i).append('\n')
            builder.append("http://example.com/").append(i).append(".m3u8\n")
        }

        val channels = parser.parse(builder.toString())

        assertEquals(22000, channels.size)
        assertTrue(channels[21999].name.startsWith("Canal"))
    }
}
