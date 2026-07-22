package com.subtlesight.observability;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class UsageCalculator {
    private UsageCalculator(){}
    public static BigDecimal cost(long inputTokens,long outputTokens,BigDecimal inputPerMillion,BigDecimal outputPerMillion){if(inputTokens<0||outputTokens<0||inputPerMillion.signum()<0||outputPerMillion.signum()<0)throw new IllegalArgumentException("usage and prices must be non-negative");return inputPerMillion.multiply(BigDecimal.valueOf(inputTokens)).add(outputPerMillion.multiply(BigDecimal.valueOf(outputTokens))).divide(BigDecimal.valueOf(1_000_000),8,RoundingMode.HALF_UP);}
}

