package com.subtlesight.provider.search;

import com.subtlesight.application.Ports.WebSearchProvider;
import com.subtlesight.domain.Models.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Adapter for a normalized search endpoint returning {results:[{title,url,snippet,publishedAt,tier,score}]}. */
public final class JsonWebSearchProvider implements WebSearchProvider {
    private final String name;private final URI endpoint;private final String apiKey;private final ObjectMapper json;private final HttpClient http;
    public JsonWebSearchProvider(String name,URI endpoint,String apiKey,ObjectMapper json,Duration timeout){this.name=name;this.endpoint=endpoint;this.apiKey=apiKey;this.json=json;this.http=HttpClient.newBuilder().connectTimeout(timeout).build();}
    @Override public String name(){return name;}
    @Override public List<SearchHit> search(QuerySpec query,int limit){try{String separator=endpoint.toString().contains("?")?"&":"?";URI uri=URI.create(endpoint+separator+"q="+URLEncoder.encode(query.query(),StandardCharsets.UTF_8)+"&limit="+limit);var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().header("Accept","application/json");if(apiKey!=null&&!apiKey.isBlank())builder.header("Authorization","Bearer "+apiKey);HttpResponse<String> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()==429||response.statusCode()>=500)throw new IllegalStateException("retryable search status "+response.statusCode());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalArgumentException("search rejected "+response.statusCode());JsonNode rows=json.readTree(response.body()).path("results");List<SearchHit> hits=new ArrayList<>();for(JsonNode row:rows){Instant published=null;try{if(row.hasNonNull("publishedAt"))published=Instant.parse(row.get("publishedAt").asText());}catch(Exception ignored){}SourceTier tier;try{tier=SourceTier.valueOf(row.path("tier").asText("UNKNOWN"));}catch(Exception e){tier=SourceTier.UNKNOWN;}hits.add(new SearchHit(name,query.type(),row.path("title").asText(),row.path("url").asText(),row.path("snippet").asText(),published,tier,row.path("score").asDouble(.5)));}return List.copyOf(hits);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("search interrupted",e);}catch(Exception e){throw new IllegalStateException("search provider failed",e);}}
    @Override public String toString(){return "JsonWebSearchProvider{"+name+", endpoint="+endpoint+", apiKey=***}";}
}

