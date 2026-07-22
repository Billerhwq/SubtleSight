package com.subtlesight.provider.search;

import com.subtlesight.application.Ports.Publisher;
import com.subtlesight.domain.Models.ReportVersion;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public final class WebhookPublisher implements Publisher {
    private final URI endpoint;private final ObjectMapper json;private final HttpClient http=HttpClient.newHttpClient();
    public WebhookPublisher(URI endpoint,ObjectMapper json){this.endpoint=endpoint;this.json=json;}
    @Override public String destinationType(){return "webhook";}
    @Override public PublishReceipt publish(ReportVersion report,String destinationId,String key){try{String body=json.writeValueAsString(Map.of("idempotencyKey",key,"reportId",report.id(),"title",report.title(),"markdown",report.markdown()));HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body)).build();HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("publisher HTTP "+response.statusCode());return new PublishReceipt(response.headers().firstValue("X-Remote-Id").orElse(key),"PUBLISHED",response.body());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("publisher interrupted",e);}catch(Exception e){throw new IllegalStateException("publisher status unknown",e);}}
    @Override public Optional<PublishReceipt> reconcile(String destinationId,String key){return Optional.empty();}
}

