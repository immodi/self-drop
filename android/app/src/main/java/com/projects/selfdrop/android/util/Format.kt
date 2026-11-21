package com.projects.selfdrop.android.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val urlPattern = Regex("""https?://[^\s<>"']+""")

/** Renders URLs in a text item as tappable links without a WebView. */
fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    urlPattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        withLink(
            LinkAnnotation.Url(
                url = match.value,
                styles = TextLinkStyles(
                    style = SpanStyle(color = linkColor),
                ),
            ),
        ) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value >= 10) {
        String.format(Locale.US, "%.0f %s", value, units[index])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

fun formatTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val zone = ZoneId.systemDefault()
    val timestamp = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val now = ZonedDateTime.now(zone)

    return when {
        timestamp.toLocalDate() == now.toLocalDate() ->
            timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))

        timestamp.year == now.year ->
            timestamp.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

        else -> timestamp.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

fun hostOf(baseUrl: String): String =
    baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')
