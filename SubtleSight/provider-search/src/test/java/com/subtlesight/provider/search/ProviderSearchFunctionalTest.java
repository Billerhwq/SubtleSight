package com.subtlesight.provider.search;

import com.subtlesight.domain.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.*;
import java.util.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class ProviderSearchFunctionalTest {
  @Test void normalizedSearchParsesFallbacksAuthAndErrors(){WireMockServer wm=new WireMockServer(0);wm.start();try{wm.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("{\"results\":[{\"title\":\"A\",\"url\":\"https://x.test\",\"snippet\":\"S\",\"publishedAt\":\"2026-07-16T00:00:00Z\",\"tier\":\"PRIMARY\",\"score\":0.9},{\"title\":\"B\",\"url\":\"https://y.test\",\"publishedAt\":\"bad\",\"tier\":\"BOGUS\"}]}")));var provider=new JsonWebSearchProvider("fixture",URI.create(wm.baseUrl()+"/search?lang=zh"),"secret",new ObjectMapper(),Duration.ofSeconds(2));var hits=provider.search(new QuerySpec("NEWS","AI 安全",Set.of(),Set.of()),2);assertThat(provider.name()).isEqualTo("fixture");assertThat(provider.toString()).contains("apiKey=***").doesNotContain("secret");assertThat(hits).hasSize(2);assertThat(hits.get(0).publishedAt()).isNotNull();assertThat(hits.get(1).tier()).isEqualTo(SourceTier.UNKNOWN);wm.verify(getRequestedFor(urlPathEqualTo("/search")).withHeader("Authorization",equalTo("Bearer secret")));wm.resetAll();wm.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(503)));assertThatThrownBy(()->provider.search(new QuerySpec("NEWS","x",Set.of(),Set.of()),1)).isInstanceOf(IllegalStateException.class);}finally{wm.stop();}}
  @Test void webhookPublishesWithIdempotencyAndRejectsFailure(){WireMockServer wm=new WireMockServer(0);wm.start();try{wm.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(200).withHeader("X-Remote-Id","remote-7").withBody("{\"ok\":true}")));WebhookPublisher publisher=new WebhookPublisher(URI.create(wm.baseUrl()+"/hook"),new ObjectMapper());Instant now=Instant.now();ReportVersion report=new ReportVersion(UUID.randomUUID(),UUID.randomUUID(),"R",1,"Title","{}","# body","<p/>","hash",true,now);var receipt=publisher.publish(report,"dest","key-1");assertThat(publisher.destinationType()).isEqualTo("webhook");assertThat(receipt.remoteId()).isEqualTo("remote-7");assertThat(publisher.reconcile("dest","key-1")).isEmpty();wm.verify(postRequestedFor(urlEqualTo("/hook")).withHeader("Idempotency-Key",equalTo("key-1")));wm.resetAll();wm.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(500)));assertThatThrownBy(()->publisher.publish(report,"dest","key-2")).isInstanceOf(IllegalStateException.class);}finally{wm.stop();}}
}
