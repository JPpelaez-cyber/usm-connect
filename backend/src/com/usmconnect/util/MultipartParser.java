package com.usmconnect.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Very small multipart/form-data parser -- just enough to pull a single
 * uploaded file (plus a few text fields) out of a POST body. Not a general
 * purpose MIME parser, but sufficient for the "upload profile picture"
 * endpoint and avoids pulling in an external dependency.
 */
public final class MultipartParser {

    public static final class Part {
        public String name;
        public String filename; // null for plain text fields
        public String contentType;
        public byte[] data;

        public String asText() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    public static List<Part> parse(byte[] body, String boundary) {
        List<Part> parts = new ArrayList<>();
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        List<Integer> boundaryIndices = findAll(body, boundaryBytes);

        for (int i = 0; i < boundaryIndices.size() - 1; i++) {
            int start = boundaryIndices.get(i) + boundaryBytes.length;
            int end = boundaryIndices.get(i + 1);
            if (start >= body.length) continue;
            // Skip leading CRLF after boundary marker.
            if (start + 1 < body.length && body[start] == '\r' && body[start + 1] == '\n') start += 2;
            if (start >= end) continue;

            byte[] section = Arrays.copyOfRange(body, start, Math.max(start, end));
            int headerEnd = indexOf(section, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), 0);
            if (headerEnd < 0) continue;

            String headerText = new String(section, 0, headerEnd, StandardCharsets.UTF_8);
            int dataStart = headerEnd + 4;
            int dataEnd = section.length;
            // strip trailing CRLF before next boundary
            if (dataEnd >= 2 && section[dataEnd - 2] == '\r' && section[dataEnd - 1] == '\n') dataEnd -= 2;
            if (dataStart > dataEnd) dataStart = dataEnd;

            Part part = new Part();
            part.data = Arrays.copyOfRange(section, dataStart, dataEnd);

            for (String line : headerText.split("\r\n")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-disposition")) {
                    part.name = extract(line, "name=\"", "\"");
                    part.filename = extract(line, "filename=\"", "\"");
                } else if (lower.startsWith("content-type")) {
                    part.contentType = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            if (part.name != null) parts.add(part);
        }
        return parts;
    }

    public static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        int idx = contentType.indexOf("boundary=");
        if (idx < 0) return null;
        String b = contentType.substring(idx + "boundary=".length());
        if (b.startsWith("\"") && b.endsWith("\"")) b = b.substring(1, b.length() - 1);
        return b.trim();
    }

    private static String extract(String line, String prefix, String suffix) {
        int start = line.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = line.indexOf(suffix, start);
        if (end < 0) return null;
        return line.substring(start, end);
    }

    private static List<Integer> findAll(byte[] haystack, byte[] needle) {
        List<Integer> result = new ArrayList<>();
        int idx = 0;
        while (true) {
            int found = indexOf(haystack, needle, idx);
            if (found < 0) break;
            result.add(found);
            idx = found + needle.length;
        }
        return result;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        outer:
        for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
