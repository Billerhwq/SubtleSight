package com.subtlesight.watchlist;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WatchlistServiceTest {
    @Test void calculatesEwmaAndColdStartZScore(){assertThat(WatchlistService.ewma(10,20,.2)).isEqualTo(12);assertThat(WatchlistService.zScore(10,5,0)).isZero();}
}

