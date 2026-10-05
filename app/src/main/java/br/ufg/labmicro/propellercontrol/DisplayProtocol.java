package br.ufg.labmicro.propellercontrol;

import java.util.Arrays;
import java.util.Locale;

/** Pure encoder for the byte protocol understood by the 8051 firmware. */
public final class DisplayProtocol {
    public static final int MAX_CONTENT_BYTES = 16;
    public static final byte TERMINATOR = 0x2F;

    private static final String SUPPORTED_ASCII =
            " ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789<>!?@#&()+-=$.:;%*,\"'_[]{}";

    private DisplayProtocol() { }

    public static byte[] encodeContent(String text) {
        if (text == null) throw new IllegalArgumentException("A mensagem nao pode ser nula.");
        byte[] result = new byte[MAX_CONTENT_BYTES];
        int count = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int value;
            if (codePoint == 0x2192) value = 0x7E;      // right arrow
            else if (codePoint == 0x2190) value = 0x7F; // left arrow
            else if (codePoint == 0x263A) value = 0x93; // smile
            else if (codePoint == 0x2588) value = 0xFF; // block
            else if (codePoint == '/') throw new IllegalArgumentException("/ e reservado como terminador.");
            else if (codePoint <= 0x7F && SUPPORTED_ASCII.indexOf((char) codePoint) >= 0) value = codePoint;
            else throw new IllegalArgumentException("Caractere nao suportado: " + new String(Character.toChars(codePoint)));

            if (count == MAX_CONTENT_BYTES) {
                throw new IllegalArgumentException("A mensagem excede 16 bytes.");
            }
            result[count++] = (byte) value;
        }
        return Arrays.copyOf(result, count);
    }

    public static byte[] textPacket(String text) {
        byte[] content = encodeContent(text);
        if (content.length == MAX_CONTENT_BYTES) return content;
        byte[] packet = Arrays.copyOf(content, content.length + 1);
        packet[content.length] = TERMINATOR;
        return packet;
    }

    public static byte[] defaultMessageCommand() {
        return new byte[]{(byte) 0xE0, TERMINATOR};
    }

    public static byte[] rpmCommand() {
        return new byte[]{(byte) 0xE1, TERMINATOR};
    }

    public static String toHex(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte value : data) {
            if (result.length() > 0) result.append(' ');
            result.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return result.toString();
    }
}
