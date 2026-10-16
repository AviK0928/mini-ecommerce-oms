package com.example.oms.model;

import java.math.BigDecimal;

/**
 * Money is stored as whole paise (long) to avoid floating-point errors.
 * The API exchanges rupee amounts as decimals with two places, e.g. 49.50.
 */
public final class Money {

    private Money() {
    }

    /** Converts rupees to paise. The caller must ensure at most 2 decimal places. */
    public static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }

    /** Converts paise to rupees with exactly 2 decimal places. */
    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}