package io.pinkspider.global.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class NumberFormatUtils {

    public static String comma(Long value) {
        return new DecimalFormat("#,##0").format(value);
    }

    public static String comma(Integer value) {
        return new DecimalFormat("#,##0").format(value);
    }

    public static String comma(Object value) {
        return value == null ? "0" : comma(new BigDecimal(String.valueOf(value)));
    }

    public static String comma(BigDecimal value) {
        return new DecimalFormat("#,##0").format(value.doubleValue());
    }
}
