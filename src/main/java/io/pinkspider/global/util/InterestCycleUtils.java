package io.pinkspider.global.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class InterestCycleUtils {

    public static BigDecimal getDays(LocalDateTime startDate, LocalDateTime endDate) {

        BigDecimal bigCalcDays = BigDecimal.valueOf(
            ChronoUnit.DAYS.between(LocalDate.of(startDate.getYear(), startDate.getMonth(), startDate.getDayOfMonth())
                , LocalDate.of(endDate.getYear(), endDate.getMonth(), endDate.getDayOfMonth())));

        return bigCalcDays;
    }

    public static BigDecimal getDaysBoth(LocalDateTime startDate, LocalDateTime endDate) {

        BigDecimal bigCalcDays = BigDecimal.valueOf(
            ChronoUnit.DAYS.between(LocalDate.of(startDate.getYear(), startDate.getMonth(), startDate.getDayOfMonth())
                , LocalDate.of(endDate.getYear(), endDate.getMonth(), endDate.getDayOfMonth()))).add(BigDecimal.ONE);
        return bigCalcDays;
    }
}
