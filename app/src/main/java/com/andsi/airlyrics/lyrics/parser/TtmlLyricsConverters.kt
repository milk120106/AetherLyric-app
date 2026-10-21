package com.andsi.airlyrics.lyrics.parser

import com.andsi.airlyrics.lyrics.WordByWordLine
import com.andsi.airlyrics.lyrics.WordByWordSegment
import java.util.Locale

internal fun ParsedTtmlDocument.toPlainLrc(): String {
    return lines
        .filter { line ->
            line.text.isNotBlank() || line.translations.any { it.isNotBlank() }
        }
        .sortedBy { it.startMs }
        .joinToString("\n") { line ->
            val original = line.text.trim()
            val translations = line.translations
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != original }
                .distinct()
                .toList()
            val storedText = when {
                original.isNotBlank() && translations.isNotEmpty() -> {
                    "$original / ${translations.joinToString(" / ")}"
                }
                original.isNotBlank() -> original
                translations.isNotEmpty() -> translations.joinToString(" / ")
                else -> ""
            }
            "[${formatTtmlLrcTime(line.startMs)}]$storedText"
        }
}

internal fun ParsedTtmlDocument.toParsedWordByWordLyrics(): ParsedWordByWordLyrics {
    val visibleLines = lines.filter { line ->
        line.text.isNotBlank() || line.translations.any { it.isNotBlank() }
    }
    val hasCompleteWordTiming = visibleLines.isNotEmpty() && visibleLines.all { line ->
        line.text.isNotBlank() && line.segments.any { it.text.isNotBlank() }
    }
    val wordByWordLines = if (hasCompleteWordTiming) {
        visibleLines
            .sortedBy { it.startMs }
            .map { line ->
                WordByWordLine(
                    startMs = line.startMs,
                    endMs = line.endMs,
                    text = line.text,
                    segments = line.segments.map { segment ->
                        WordByWordSegment(
                            text = segment.text,
                            startMs = segment.startMs,
                            endMs = segment.endMs
                        )
                    }
                )
            }
    } else {
        emptyList()
    }

    val hasTranslation = lines.any { line ->
        line.translations.any { translation ->
            translation.isNotBlank() && translation.trim() != line.text.trim()
        }
    }

    return ParsedWordByWordLyrics(
        wordByWordLines = wordByWordLines,
        plainLrc = toPlainLrc(),
        hasTranslation = hasTranslation
    )
}

internal fun ParsedTtmlDocument.toTtmlString(): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    sb.append("<tt xmlns=\"http://www.w3.org/ns/ttml\"\n")
    sb.append("    xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"\n")
    sb.append("    xmlns:itunes=\"http://itunes.apple.com/lyric-ttml-extensions\">\n")
    sb.append("  <body>\n")
    sb.append("    <div>\n")
    lines.filter { it.text.isNotBlank() || it.translations.isNotEmpty() }.sortedBy { it.startMs }.forEach { line ->
        val beginStr = formatTtmlTime(line.startMs)
        val endStr = formatTtmlTime(line.endMs)
        sb.append("      <p begin=\"$beginStr\" end=\"$endStr\">")
        if (line.segments.isNotEmpty()) {
            line.segments.forEach { seg ->
                val segBegin = formatTtmlTime(seg.startMs)
                val segEnd = formatTtmlTime(seg.endMs)
                sb.append("<span begin=\"$segBegin\" end=\"$segEnd\">${escapeXml(seg.text)}</span>")
            }
        } else {
            sb.append(escapeXml(line.text))
        }
        line.translations.filter { it.isNotBlank() }.forEach { trans ->
            sb.append("<span ttm:role=\"x-translation\">${escapeXml(trans)}</span>")
        }
        sb.append("</p>\n")
    }
    sb.append("    </div>\n")
    sb.append("  </body>\n")
    sb.append("</tt>\n")
    return sb.toString()
}

internal fun lrcToTtml(lrcContent: String): String {
    val parsedLines = LrcParser.parse(lrcContent)
    val eligibleLines = parsedLines.filter { !it.isMetadata && (it.text.isNotBlank() || it.hasTranslation()) }
    if (eligibleLines.isEmpty()) {
        val rawLines = lrcContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<tt xmlns=\"http://www.w3.org/ns/ttml\"\n")
        sb.append("    xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">\n")
        sb.append("  <body>\n    <div>\n")
        var currentMs = 0L
        rawLines.forEach { line ->
            sb.append("      <p begin=\"${formatTtmlTime(currentMs)}\" end=\"${formatTtmlTime(currentMs + 3000L)}\">${escapeXml(line)}</p>\n")
            currentMs += 3000L
        }
        sb.append("    </div>\n  </body>\n</tt>\n")
        return sb.toString()
    }

    val ttmlLines = eligibleLines.mapIndexed { index, line ->
        val nextTimeMs = eligibleLines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 4000L)
        val endMs = if (nextTimeMs > line.timeMs) nextTimeMs else line.timeMs + 3000L
        val originalText = line.text.trim()
        val translations = if (line.hasTranslation() && !line.translation.isNullOrBlank()) listOf(line.translation.trim()) else emptyList()
        ParsedTtmlLine(
            startMs = line.timeMs,
            endMs = endMs,
            text = originalText,
            translations = translations
        )
    }
    return ParsedTtmlDocument(ttmlLines).toTtmlString()
}

internal fun formatTtmlTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000.0
    return String.format(Locale.ROOT, "%.3fs", totalSeconds)
}

internal fun escapeXml(text: String): String {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun formatTtmlLrcTime(timeMs: Long): String {
    val minutes = timeMs / 60_000L
    val seconds = (timeMs % 60_000L) / 1_000L
    val centiseconds = (timeMs % 1_000L) / 10L
    return "%02d:%02d.%02d".format(Locale.ROOT, minutes, seconds, centiseconds)
}
