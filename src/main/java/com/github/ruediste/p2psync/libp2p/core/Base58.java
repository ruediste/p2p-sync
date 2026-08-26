package com.github.ruediste.p2psync.libp2p.core;

import java.util.Arrays;

/**
 * Bitcoin-style Base58 encoding/decoding.
 *
 * <p>
 * Adapted from {@code io.libp2p.etc.encode.Base58} (jvm-libp2p), itself adapted from
 * https://github.com/bitcoinj/bitcoinj/.
 */
public final class Base58 {

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final byte ZERO_BYTE = 0;
    private static final char ENCODED_ZERO = ALPHABET.charAt(0);
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            INDEXES[ALPHABET.charAt(i)] = i;
        }
    }

    private Base58() {
    }

    public static String encode(byte[] input) {
        if (input.length == 0) {
            return "";
        }

        // Count leading zeros.
        int zeros = 0;
        while (zeros < input.length && input[zeros] == ZERO_BYTE) {
            zeros++;
        }

        // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
        byte[] work = Arrays.copyOf(input, input.length); // since we modify it in-place
        char[] encoded = new char[work.length * 2]; // upper bound
        int outputStart = encoded.length;
        int inputStart = zeros;
        while (inputStart < work.length) {
            encoded[--outputStart] = ALPHABET.charAt(divmod(work, inputStart, 256, 58));
            if (work[inputStart] == ZERO_BYTE) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
        while (outputStart < encoded.length && encoded[outputStart] == ENCODED_ZERO) {
            ++outputStart;
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = ENCODED_ZERO;
        }
        // Return encoded string (including encoded leading zeros).
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    public static byte[] decode(String input) {
        if (input.isEmpty()) {
            return new byte[0];
        }

        // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
        byte[] input58 = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int v = c < 128 ? INDEXES[c] : -1;
            if (v < 0) {
                throw new IllegalArgumentException("invalid base58 encoded form");
            }
            input58[i] = (byte) v;
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input58.length && input58[zeros] == ZERO_BYTE) {
            ++zeros;
        }

        // Convert base-58 digits to base-256 digits.
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;
        int inputStart = zeros;
        while (inputStart < input58.length) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
            if (input58[inputStart] == ZERO_BYTE) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Ignore extra leading zeroes that were added during the calculation.
        while (outputStart < decoded.length && decoded[outputStart] == ZERO_BYTE) {
            ++outputStart;
        }
        // Return decoded data (including original number of leading zeros).
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    /**
     * This is just long division which accounts for the base of the input digits.
     */
    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
            int digit = number[i] & 0xFF;
            int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }
        return (byte) remainder;
    }
}
