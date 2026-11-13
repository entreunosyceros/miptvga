package com.toigo.miptvga

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Reader
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.TimeZone

private const val ProgrammeTag = "programme"
private const val ChannelTag = "channel"
private const val DisplayNameTag = "display-name"
private const val TitleTag = "title"
private const val XmltvRelevantPastHours = 3L
private const val XmltvRelevantFutureHours = 36L

internal class XmltvParser {
    fun parseProgramTimelines(
        reader: Reader,
        interests: EpgChannelInterests,
        nowMillis: Long = System.currentTimeMillis(),
        checkCancelled: () -> Unit = {}
    ): Map<String, List<CurrentProgram>> {
        if (interests.ids.isEmpty() && interests.names.isEmpty()) return emptyMap()

        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(reader)
        }
        val matchedKeysByXmlChannelId = HashMap<String, MutableSet<String>>()
        val timelines = HashMap<String, MutableList<CurrentProgram>>()
        val minimumStopTime = nowMillis - XmltvRelevantPastHours * 60L * 60L * 1_000L
        val maximumStartTime = nowMillis + XmltvRelevantFutureHours * 60L * 60L * 1_000L

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            checkCancelled()
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    ChannelTag -> parseChannel(parser, interests, matchedKeysByXmlChannelId, checkCancelled)
                    ProgrammeTag -> parseProgramme(
                        parser = parser,
                        interests = interests,
                        matchedKeysByXmlChannelId = matchedKeysByXmlChannelId,
                        timelines = timelines,
                        minimumStopTime = minimumStopTime,
                        maximumStartTime = maximumStartTime,
                        checkCancelled = checkCancelled
                    )
                }
            }
            eventType = parser.next()
        }

        return timelines.mapValues { (_, programs) ->
            checkCancelled()
            programs.sortedBy { it.startTimeMillis }
        }
    }

    private fun parseChannel(
        parser: XmlPullParser,
        interests: EpgChannelInterests,
        matchedKeysByXmlChannelId: MutableMap<String, MutableSet<String>>,
        checkCancelled: () -> Unit
    ) {
        val xmlChannelId = parser.getAttributeValue(null, "id")?.trim().orEmpty()
        if (xmlChannelId.isBlank()) return

        val matchedKeys = LinkedHashSet<String>()
        val normalizedChannelId = xmlChannelId.normalizeEpgLookupKey()
        if (normalizedChannelId in interests.ids) {
            matchedKeys += normalizedChannelId
        }

        val startDepth = parser.depth
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth && parser.name == ChannelTag)) {
            checkCancelled()
            val nextEvent = parser.next()
            check(nextEvent != XmlPullParser.END_DOCUMENT) { "XMLTV incompleto" }
            if (nextEvent == XmlPullParser.START_TAG && parser.name == DisplayNameTag) {
                val displayName = parser.nextText().normalizeEpgLookupKey()
                if (displayName in interests.names) {
                    matchedKeys += displayName
                }
            }
        }

        if (matchedKeys.isNotEmpty()) {
            matchedKeysByXmlChannelId.getOrPut(xmlChannelId) { LinkedHashSet() }.addAll(matchedKeys)
        }
    }

    private fun parseProgramme(
        parser: XmlPullParser,
        interests: EpgChannelInterests,
        matchedKeysByXmlChannelId: Map<String, Set<String>>,
        timelines: MutableMap<String, MutableList<CurrentProgram>>,
        minimumStopTime: Long,
        maximumStartTime: Long,
        checkCancelled: () -> Unit
    ) {
        val xmlChannelId = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
        val startTime = parseXmltvDateTime(parser.getAttributeValue(null, "start")) ?: return
        val endTime = parseXmltvDateTime(parser.getAttributeValue(null, "stop")) ?: return
        val normalizedChannelId = xmlChannelId.normalizeEpgLookupKey()
        val matchedKeys = LinkedHashSet<String>().apply {
            if (normalizedChannelId in interests.ids) add(normalizedChannelId)
            matchedKeysByXmlChannelId[xmlChannelId]?.let(::addAll)
        }

        var title = ""
        val startDepth = parser.depth
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth && parser.name == ProgrammeTag)) {
            checkCancelled()
            val nextEvent = parser.next()
            check(nextEvent != XmlPullParser.END_DOCUMENT) { "XMLTV incompleto" }
            if (nextEvent == XmlPullParser.START_TAG && parser.name == TitleTag && title.isBlank()) {
                title = parser.nextText().trim()
            }
        }

        if (
            matchedKeys.isEmpty() ||
                title.isBlank() ||
                endTime < minimumStopTime ||
                startTime > maximumStartTime ||
                endTime <= startTime
        ) {
            return
        }

        val program = CurrentProgram(
            title = title,
            startTimeMillis = startTime,
            endTimeMillis = endTime
        )
        matchedKeys.forEach { key ->
            timelines.getOrPut(key) { ArrayList() }.add(program)
        }
    }

    private fun parseXmltvDateTime(value: String?): Long? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.length < 14) return null

        val datePart = trimmed.take(14)
        if (!datePart.all(Char::isDigit)) return null
        val offset = trimmed.substring(14).trim()
        // XMLTV defines UTC when the zone is absent. Accept the common ISO colon
        // extension as well, without converting RFC822 offsets into the wrong format.
        val normalizedOffset = when {
            offset.isEmpty() || offset == "Z" -> "+0000"
            OffsetPattern.matches(offset) -> offset.replace(":", "")
            else -> return null
        }
        val text = "$datePart $normalizedOffset"
        val position = ParsePosition(0)
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.parse(text, position)?.takeIf { position.index == text.length }?.time
    }
}

internal data class EpgChannelInterests(
    val ids: Set<String>,
    val names: Set<String>
)

private val OffsetPattern = Regex("[+-](?:[01][0-9]|2[0-3]):?[0-5][0-9]")

