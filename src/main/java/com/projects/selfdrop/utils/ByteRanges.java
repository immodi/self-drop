package com.projects.selfdrop.utils;

import org.springframework.http.HttpRange;

import java.util.List;

/**
 * Resolves a single HTTP byte range for a response.
 *
 * <p>Multiple ranges are ignored (the full content is sent), and malformed
 * headers are ignored as allowed by RFC 9110.</p>
 */
public final class ByteRanges {

    private ByteRanges() {
    }

    public static Selection select(String header, long size) {
        if (header == null || header.isBlank()) {
            return Selection.full(size);
        }

        try {
            List<HttpRange> ranges = HttpRange.parseRanges(normalizeRangeUnit(header));
            if (ranges.size() != 1) {
                return Selection.full(size);
            }

            HttpRange requested = ranges.get(0);
            long start = requested.getRangeStart(size);
            long end = requested.getRangeEnd(size);
            if (size <= 0 || start < 0 || start >= size || start > end) {
                return Selection.unsatisfiableRange();
            }

            return Selection.partial(start, end);
        } catch (IllegalArgumentException ignored) {
            return Selection.full(size);
        }
    }

    private static String normalizeRangeUnit(String header) {
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "bytes=", 0, 6)) {
            return "bytes=" + trimmed.substring(6);
        }
        return trimmed;
    }

    public record Selection(boolean partial, boolean unsatisfiable, long start, long length) {
        public static Selection full(long size) {
            return new Selection(false, false, 0, size);
        }

        public static Selection partial(long start, long end) {
            return new Selection(true, false, start, end - start + 1);
        }

        public static Selection unsatisfiableRange() {
            return new Selection(false, true, 0, 0);
        }
    }
}
