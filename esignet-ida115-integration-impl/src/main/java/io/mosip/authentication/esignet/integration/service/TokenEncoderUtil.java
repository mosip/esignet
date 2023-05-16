package io.mosip.authentication.esignet.integration.service;

import java.util.Arrays;
import java.util.Objects;

/**
 * The Class TokenEncoderUtil is the utility class to encode base58 for the generated token.
 * 
 * @author Mahammed Taheer
 */
public final class TokenEncoderUtil {
    
    private static final char[] BASE58_ALPHANUMERIC = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    private static final char ENCODED_0 = BASE58_ALPHANUMERIC[0];

    public static String encodeBase58(final byte[] dataToEncode) {
        if (Objects.isNull(dataToEncode) || dataToEncode.length == 0) {
            return "";
        }

        final char[] encoded = new char[dataToEncode.length * 2];
        final byte[] copy = Arrays.copyOf(dataToEncode, dataToEncode.length); 

        int zeros = 0;
        while (zeros < dataToEncode.length && dataToEncode[zeros] == 0) {
            ++zeros;
        }

        int inputIndex = zeros;
        int outputIndex = encoded.length;
        while (inputIndex < copy.length) {
            encoded[--outputIndex] = BASE58_ALPHANUMERIC[divmod(copy, inputIndex, 256, 58)];
            if (copy[inputIndex] == 0) {
                ++inputIndex; 
            }
        }

        while (outputIndex < encoded.length && encoded[outputIndex] == ENCODED_0) {
            ++outputIndex;
        }
        while (--zeros >= 0) {
            encoded[--outputIndex] = ENCODED_0;
        }

        return new String(encoded, outputIndex, encoded.length - outputIndex);
    }

    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
          int digit = (int) number[i] & 0xff;
          int temp = remainder * base + digit;
          number[i] = (byte) (temp / divisor);
          remainder = temp % divisor;
        }
        return (byte) remainder;
    }
    

}
