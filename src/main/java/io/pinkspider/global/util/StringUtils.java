package io.pinkspider.global.util;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

    private static final String EUC_KR = "EUC-KR";

    public static String getRpadString(String padString, int byteLength) throws UnsupportedEncodingException {
        if (padString == null) {
            padString = " ";
        }
        byte[] padBytes = padString.getBytes(EUC_KR);
        int repeatLength = byteLength - padBytes.length;
        if (repeatLength < 0) {
            return new String(Arrays.copyOf(padBytes, byteLength), EUC_KR);
        }
        return padString + " ".repeat(repeatLength);
    }

    public static String getLpadString(String padString, int byteLength) throws UnsupportedEncodingException {
        if (padString == null) {
            padString = " ";
        }
        int repeatLength = byteLength - padString.getBytes(EUC_KR).length;
        return "0".repeat(repeatLength) + padString;
    }
}
