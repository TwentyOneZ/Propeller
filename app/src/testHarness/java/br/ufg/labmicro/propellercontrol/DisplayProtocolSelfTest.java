package br.ufg.labmicro.propellercontrol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class DisplayProtocolSelfTest {
    private static int checks;

    public static void main(String[] args) {
        expectHex("TESTE", "54 45 53 54 45 2F");

        byte[] exact = DisplayProtocol.textPacket("1234567890ABCDEF");
        check(exact.length == 16, "16-byte input must not grow");
        check(Arrays.equals(exact, "1234567890ABCDEF".getBytes(StandardCharsets.US_ASCII)),
                "16-byte payload changed");

        check("E0 2F".equals(DisplayProtocol.toHex(DisplayProtocol.defaultMessageCommand())), "E0 command");
        check("E1 2F".equals(DisplayProtocol.toHex(DisplayProtocol.rpmCommand())), "E1 command");
        expectHex("05-10 13:58:42", "30 35 2D 31 30 20 31 33 3A 35 38 3A 34 32 2F");
        expectHex("\u2192\u2190\u263A\u2588", "7E 7F 93 FF 2F");
        expectRejected("A/B");
        expectRejected("ol\u00E1");
        expectRejected("1234567890ABCDEFG");

        System.out.println("DisplayProtocolSelfTest: " + checks + " verificacoes aprovadas.");
    }

    private static void expectHex(String input, String expected) {
        check(expected.equals(DisplayProtocol.toHex(DisplayProtocol.textPacket(input))), "hex for " + input);
    }

    private static void expectRejected(String input) {
        try {
            DisplayProtocol.textPacket(input);
            throw new AssertionError("Expected rejection for: " + input);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
