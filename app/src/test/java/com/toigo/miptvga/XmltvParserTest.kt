package com.toigo.miptvga

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.CancellationException

class XmltvParserTest {
    private val now = 1794592800000L // 2026-11-13 18:00 UTC
    private fun parse(start: String, stop: String) = XmltvParser().parseProgramTimelines(
        ("<tv><channel id=\"id1\"><display-name>Uno</display-name></channel>" +
            "<programme channel=\"id1\" start=\"$start\" stop=\"$stop\"><title>Noticias</title></programme></tv>").reader(),
        EpgChannelInterests(setOf("id1"), setOf("uno")), now
    )

    @Test fun acceptsRfc822AndColonOffsets() {
        for (zone in listOf("+0100", "+01:00")) {
            val result = parse("20261113190000 $zone", "20261113200000 $zone")
            assertEquals(now, result.getValue("id1").single().startTimeMillis)
            assertEquals(result["id1"], result["uno"])
        }
    }

    @Test fun acceptsNegativeAndUtcOffsets() {
        for ((time, zone) in listOf("150000" to "-0300", "180000" to "+0000", "180000" to "Z")) {
            assertEquals(now, parse("20261113$time $zone", "20261113220000 Z").getValue("id1").single().startTimeMillis)
        }
    }

    @Test fun absentOffsetUsesUtcRegardlessOfDeviceTimezone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(now, parse("20261113180000", "20261113190000").getValue("id1").single().startTimeMillis)
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun dstOffsetsRemainExplicit() {
        val a = parse("20261025023000 +0200", "20261113190000 +0000").getValue("id1").single()
        val b = parse("20261025023000 +0100", "20261113190000 +0000").getValue("id1").single()
        assertEquals(3600000L, b.startTimeMillis - a.startTimeMillis)
    }

    @Test fun rejectsInvalidDatesOffsetsAndReversedIntervals() {
        for (value in listOf("20260230180000 +0000", "20261113180000 +2500", "20261113180000 +0060", "20261113180000 garbage", "oops")) {
            assertTrue(parse(value, "20261113190000 +0000").isEmpty())
        }
        assertTrue(parse("20261113190000 +0000", "20261113180000 +0000").isEmpty())
    }

    @Test fun cancellationEscapesDuringNestedXml() {
        var checks = 0
        try {
            XmltvParser().parseProgramTimelines("<tv><channel id=\"id1\"><display-name>Uno</display-name></channel></tv>".reader(), EpgChannelInterests(setOf("id1"), emptySet()), now) {
                if (++checks == 4) throw CancellationException()
            }
            fail("Cancellation expected")
        } catch (_: CancellationException) { assertEquals(4, checks) }
    }

    @Test(expected = org.xmlpull.v1.XmlPullParserException::class)
    fun truncatedXmlTerminatesWithError() {
        XmltvParser().parseProgramTimelines("<tv><channel id=\"id1\"><display-name>Uno".reader(), EpgChannelInterests(setOf("id1"), emptySet()), now)
    }
}
