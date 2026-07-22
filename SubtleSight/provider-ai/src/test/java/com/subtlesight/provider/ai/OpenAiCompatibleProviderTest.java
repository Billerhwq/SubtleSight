package com.subtlesight.provider.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.subtlesight.application.Ports.AiProvider;

class OpenAiCompatibleProviderTest {
    @Test void neverLeaksApiKeyThroughStringSerialization(){var p=new OpenAiCompatibleProvider("test",URI.create("https://example.com/v1"),"secret-key","model",new ObjectMapper(),Duration.ofSeconds(1));assertThat(p.toString()).doesNotContain("secret-key").contains("***");}
    @Test void completesJsonRequestAndClassifiesProviderErrors(){WireMockServer wm=new WireMockServer(0);wm.start();try{wm.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("{\"model\":\"fixture\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}")));var p=new OpenAiCompatibleProvider("wire",URI.create(wm.baseUrl()+"/v1"),"key","model",new ObjectMapper(),Duration.ofSeconds(2));var result=p.complete(new AiProvider.AiRequest("test","system","prompt","{ok:boolean}",100,0));assertThat(result.content()).contains("ok");assertThat(result.inputTokens()).isEqualTo(3);assertThat(result.outputTokens()).isEqualTo(4);wm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")).withHeader("Authorization",equalTo("Bearer key")));for(int status:new int[]{401,429,400}){wm.resetAll();wm.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(status)));assertThatThrownBy(()->p.complete(new AiProvider.AiRequest("x","s","p",null,1,0))).isInstanceOf(OpenAiCompatibleProvider.ProviderException.class).satisfies(e->{var pe=(OpenAiCompatibleProvider.ProviderException)e;assertThat(pe.code()).isEqualTo((status==401?"AUTH_":"HTTP_")+status);assertThat(pe.retryable()).isEqualTo(status==429);});}}finally{wm.stop();}}
    @Test void blankKeyIsRejected(){var p=new OpenAiCompatibleProvider("x",URI.create("https://example.com/v1")," ","m",new ObjectMapper(),Duration.ofSeconds(1));assertThatThrownBy(()->p.complete(new AiProvider.AiRequest("x","s","p",null,1,0))).isInstanceOf(IllegalStateException.class);}
}
