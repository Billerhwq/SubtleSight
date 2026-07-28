package com.subtlesight.provider.ai;

import com.subtlesight.application.Ports.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Works with OpenAI, DeepSeek, Qwen and other OpenAI-compatible chat endpoints. */
public final class OpenAiCompatibleProvider implements AiProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private final String provider;private final URI endpoint;private final String apiKey;private final String model;private final ObjectMapper json;private final HttpClient http;
    public OpenAiCompatibleProvider(String provider,URI baseUrl,String apiKey,String model,ObjectMapper json,Duration timeout){this.provider=provider;this.endpoint=baseUrl.resolve(baseUrl.getPath().endsWith("/")?"chat/completions":"/v1/chat/completions");this.apiKey=apiKey;this.model=model;this.json=json;this.http=HttpClient.newBuilder().connectTimeout(timeout).build();}
    @Override public AiResult complete(AiRequest request){if(apiKey==null||apiKey.isBlank())throw new IllegalStateException("AI provider is not configured");try{Map<String,Object> body=new LinkedHashMap<>();body.put("model",model);body.put("messages",List.of(Map.of("role","system","content",request.system()),Map.of("role","user","content",request.prompt())));body.put("max_tokens",request.maxTokens());body.put("temperature",request.temperature());if(request.schema()!=null&&!request.schema().isBlank())body.put("response_format",Map.of("type","json_object"));HttpRequest httpRequest=HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(3)).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();HttpResponse<String> response=http.send(httpRequest,HttpResponse.BodyHandlers.ofString());if(response.statusCode()==401||response.statusCode()==403)throw new ProviderException("AUTH_"+response.statusCode(),false);if(response.statusCode()==429||response.statusCode()>=500)throw new ProviderException("HTTP_"+response.statusCode(),true);if(response.statusCode()<200||response.statusCode()>=300)throw new ProviderException("HTTP_"+response.statusCode(),false);JsonNode root=json.readTree(response.body());String content=root.path("choices").path(0).path("message").path("content").asText();String finishReason=root.path("choices").path(0).path("finish_reason").asText();long input=root.path("usage").path("prompt_tokens").asLong();long output=root.path("usage").path("completion_tokens").asLong();if("length".equals(finishReason)){log.warn("AI response truncated by max_tokens: purpose={}, finishReason={}, inputTokens={}, outputTokens={}",request.purpose(),finishReason,input,output);throw new ProviderException("LENGTH_TRUNCATED",true);}return new AiResult(content,input,output,root.path("model").asText(model),provider);}catch(ProviderException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProviderException("INTERRUPTED",true);}catch(Exception e){throw new ProviderException("PROVIDER_IO",true,e);}}
    public static final class ProviderException extends RuntimeException{private final String code;private final boolean retryable;public ProviderException(String code,boolean retryable){super(code);this.code=code;this.retryable=retryable;}public ProviderException(String code,boolean retryable,Throwable cause){super(code,cause);this.code=code;this.retryable=retryable;}public String code(){return code;}public boolean retryable(){return retryable;}}
    @Override public String toString(){return "OpenAiCompatibleProvider{"+provider+", endpoint="+endpoint+", model="+model+", apiKey=***}";}
}

