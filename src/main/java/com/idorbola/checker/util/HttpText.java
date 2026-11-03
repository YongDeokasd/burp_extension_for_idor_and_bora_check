package com.idorbola.checker.util;

import burp.api.montoya.http.message.HttpMessage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Charset-aware text for HTTP messages. Montoya's toString()/bodyToString() map each byte to one
 * char (ISO-8859-1), which garbles UTF-8 and EUC-KR text such as Korean.
 */
public final class HttpText {

    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9._:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Charset KOREAN_LEGACY = Charset.isSupported("MS949") ? Charset.forName("MS949") : StandardCharsets.ISO_8859_1;

    private HttpText() {}

    /** Whole message (start line, headers, body) decoded for display. */
    public static String message(HttpMessage msg) {
        byte[] all = msg.toByteArray().getBytes();
        int offset = Math.min(Math.max(msg.bodyOffset(), 0), all.length);
        byte[] head = Arrays.copyOfRange(all, 0, offset);
        byte[] body = Arrays.copyOfRange(all, offset, all.length);
        Charset headCharset = isUtf8(head) ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        return new String(head, headCharset) + new String(body, bodyCharset(msg.headerValue("Content-Type"), body));
    }

    /** Message body decoded with its declared or detected charset. */
    public static String body(HttpMessage msg) {
        byte[] body = msg.body().getBytes();
        return new String(body, bodyCharset(msg.headerValue("Content-Type"), body));
    }

    /**
     * Path for display: percent-encoded or raw UTF-8 bytes (e.g. Korean) are shown as text.
     * ASCII escapes such as %2F or %20 are left as they are.
     */
    public static String displayPath(String path) {
        if (path == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream(path.length());
        boolean changed = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '%' && i + 2 < path.length()) {
                int b = hexByte(path.charAt(i + 1), path.charAt(i + 2));
                if (b >= 0x80) {
                    out.write(b);
                    i += 2;
                    changed = true;
                    continue;
                }
            }
            if (c > 0xFF) return path;
            out.write(c);
            if (c >= 0x80) changed = true;
        }
        byte[] bytes = out.toByteArray();
        return changed && isUtf8(bytes) ? new String(bytes, StandardCharsets.UTF_8) : path;
    }

    static Charset bodyCharset(String contentType, byte[] body) {
        Charset declared = charsetFrom(contentType);
        if (declared != null) return declared;

        // HTML often declares the charset in a <meta> tag instead of the header.
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("html") || ct.contains("xml") || ct.isEmpty()) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Charset meta = charsetFrom(head);
            if (meta != null) return meta;
        }
        if (isUtf8(body)) return StandardCharsets.UTF_8;
        return KOREAN_LEGACY;
    }

    private static Charset charsetFrom(String text) {
        if (text == null) return null;
        Matcher m = CHARSET.matcher(text);
        if (!m.find()) return null;
        String name = m.group(1);
        if (name.equalsIgnoreCase("euc-kr") || name.equalsIgnoreCase("ks_c_5601-1987")) return KOREAN_LEGACY;
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean isUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static int hexByte(char hi, char lo) {
        int h = Character.digit(hi, 16), l = Character.digit(lo, 16);
        return h < 0 || l < 0 ? -1 : (h << 4) | l;
    }
}
