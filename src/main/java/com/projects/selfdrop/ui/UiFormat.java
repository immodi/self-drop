package com.projects.selfdrop.ui;

import com.projects.selfdrop.dtos.responses.FileDto;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small formatting helpers used by the server-rendered UI.
 *
 * <p>The linkifier escapes all text before adding anchors, so item text can be
 * rendered with {@code th:utext} without opening an XSS hole.</p>
 */
public final class UiFormat {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}";

    private UiFormat() {
    }

    public static String time(Long epochSeconds) {
        if (epochSeconds == null) {
            return "";
        }
        return TIME_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    public static String iso(Long epochSeconds) {
        return epochSeconds == null ? "" : Instant.ofEpochSecond(epochSeconds).toString();
    }

    public static String size(Long bytes) {
        if (bytes == null || bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);

        return String.format(Locale.ROOT, value >= 10 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    /**
     * SVG is deliberately excluded: serving user-uploaded SVG inline can execute
     * script in the origin that hosts the UI.
     */
    public static boolean isImage(FileDto item) {
        if (item == null || !"file".equals(item.kind()) || item.mime() == null) {
            return false;
        }
        String mime = item.mime().toLowerCase(Locale.ROOT);
        return mime.startsWith("image/") && !mime.equals("image/svg+xml");
    }

    public static boolean isVideo(FileDto item) {
        if (item == null || !"file".equals(item.kind()) || item.mime() == null) {
            return false;
        }
        return item.mime().toLowerCase(Locale.ROOT).startsWith("video/");
    }

    public static boolean isPreviewable(FileDto item) {
        return isImage(item) || isVideo(item);
    }

    public static String linkify(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder(text.length() + 32);
        Matcher matcher = URL_PATTERN.matcher(text);
        int last = 0;

        while (matcher.find()) {
            html.append(HtmlUtils.htmlEscape(text.substring(last, matcher.start())));

            String url = matcher.group();
            String trailing = "";
            while (!url.isEmpty() && TRAILING_PUNCTUATION.indexOf(url.charAt(url.length() - 1)) >= 0) {
                trailing = url.charAt(url.length() - 1) + trailing;
                url = url.substring(0, url.length() - 1);
            }

            String safeUrl = HtmlUtils.htmlEscape(url);
            html.append("<a href=\"").append(safeUrl)
                    .append("\" target=\"_blank\" rel=\"noreferrer noopener\">")
                    .append(safeUrl)
                    .append("</a>");
            html.append(HtmlUtils.htmlEscape(trailing));

            last = matcher.end();
        }

        html.append(HtmlUtils.htmlEscape(text.substring(last)));
        return html.toString();
    }
}
