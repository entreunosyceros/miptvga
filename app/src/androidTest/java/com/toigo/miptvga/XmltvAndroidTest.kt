package com.toigo.miptvga

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Run on API 24 and a current TV: host JVM results do not validate libcore. */
@RunWith(AndroidJUnit4::class)
class XmltvAndroidTest {
    @Test fun parsesRfc822AndIsoOffsetsOnAndroid() {
        val now = 1794592800000L
        for (zone in listOf("+0100", "+01:00")) {
            val xml = "<tv><programme channel=\"id1\" start=\"20261113190000 $zone\" stop=\"20261113200000 $zone\"><title>Noticias</title></programme></tv>"
            val result = XmltvParser().parseProgramTimelines(xml.reader(), EpgChannelInterests(setOf("id1"), emptySet()), now)
            assertEquals(now, result.getValue("id1").single().startTimeMillis)
        }
    }
}
