package com.subtlesight.discovery;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryPlannerTest {
    @Test void plansOfficialPaperNewsCommunityAndCounterEvidence() {
        var plan=new DiscoveryPlanner(Clock.systemUTC()).plan("INTEREST","AI Agent",50);
        assertThat(plan.queries()).extracting(q->q.type()).containsExactly("GENERAL","OFFICIAL","CODE_PAPER","NEWS","COMMUNITY","COUNTER_EVIDENCE");
    }
    @Property void canonicalizationIsIdempotent(@ForAll("urls") String url) { assertThat(com.subtlesight.domain.CanonicalUrl.normalize(com.subtlesight.domain.CanonicalUrl.normalize(url))).isEqualTo(com.subtlesight.domain.CanonicalUrl.normalize(url)); }
    @net.jqwik.api.Provide net.jqwik.api.Arbitrary<String> urls(){ return net.jqwik.api.Arbitraries.of("HTTPS://Example.com:443/a/?utm_source=x&b=2","http://example.com/a/../b?z=1&a=2","example.com/path"); }
}

