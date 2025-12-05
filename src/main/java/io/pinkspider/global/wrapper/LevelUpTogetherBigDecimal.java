package io.pinkspider.global.wrapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

public final class LevelUpTogetherBigDecimal extends BigDecimal {

    private static final int ZERO = 0;

    public LevelUpTogetherBigDecimal(char[] in, int offset, int len) {
        super(in, offset, len);
    }

    public LevelUpTogetherBigDecimal(char[] in, int offset, int len, MathContext mc) {
        super(in, offset, len, mc);
    }

    public LevelUpTogetherBigDecimal(char[] in) {
        super(in);
    }

    public LevelUpTogetherBigDecimal(char[] in, MathContext mc) {
        super(in, mc);
    }

    public LevelUpTogetherBigDecimal(String val) {
        super(val);
    }

    public LevelUpTogetherBigDecimal(String val, MathContext mc) {
        super(val, mc);
    }

    public LevelUpTogetherBigDecimal(double val) {
        super(val);
    }

    public LevelUpTogetherBigDecimal(double val, MathContext mc) {
        super(val, mc);
    }

    public LevelUpTogetherBigDecimal(BigInteger val) {
        super(val);
    }

    public LevelUpTogetherBigDecimal(BigInteger val, MathContext mc) {
        super(val, mc);
    }

    public LevelUpTogetherBigDecimal(BigInteger unscaledVal, int scale) {
        super(unscaledVal, scale);
    }

    public LevelUpTogetherBigDecimal(BigInteger unscaledVal, int scale, MathContext mc) {
        super(unscaledVal, scale, mc);
    }

    public LevelUpTogetherBigDecimal(int val) {
        super(val);
    }

    public LevelUpTogetherBigDecimal(int val, MathContext mc) {
        super(val, mc);
    }

    public LevelUpTogetherBigDecimal(long val) {
        super(val);
    }

    public LevelUpTogetherBigDecimal(long val, MathContext mc) {
        super(val, mc);
    }

    public boolean eq(BigDecimal decimal) {
        return this.compareTo(decimal) == ZERO;
    }

    public boolean eq(double decimal) {
        return eq(BigDecimal.valueOf(decimal));
    }

    public boolean gt(BigDecimal decimal) {
        return this.compareTo(decimal) > ZERO;
    }

    public boolean gt(double decimal) {
        return gt(BigDecimal.valueOf(decimal));
    }

    public boolean gte(BigDecimal decimal) {
        return this.compareTo(decimal) >= ZERO;
    }

    public boolean gte(double decimal) {
        return gte(BigDecimal.valueOf(decimal));
    }

    public boolean lt(BigDecimal decimal) {
        return this.compareTo(decimal) < ZERO;
    }

    public boolean lt(double decimal) {
        return lt(BigDecimal.valueOf(decimal));
    }

    public boolean lte(BigDecimal decimal) {
        return this.compareTo(decimal) <= ZERO;
    }

    public boolean lte(double decimal) {
        return lte(BigDecimal.valueOf(decimal));
    }

    // TODO 정책을 토대로 scale기준을 정해야한다.
    public BigDecimal setScale(BigDecimal target, int scale) {
        return target.setScale(scale, RoundingMode.FLOOR);
    }
}
