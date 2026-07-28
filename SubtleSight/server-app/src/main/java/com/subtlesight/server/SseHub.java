package com.subtlesight.server;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class SseHub {
    private final Set<SseEmitter> clients=ConcurrentHashMap.newKeySet();
    private final Map<String,Set<SseEmitter>> turnEmitters=new ConcurrentHashMap<>();

    /** Global subscription (existing behavior). */
    public SseEmitter subscribe(){SseEmitter e=new SseEmitter(0L);clients.add(e);e.onCompletion(()->clients.remove(e));e.onTimeout(()->clients.remove(e));try{e.send(SseEmitter.event().name("ready").data(Map.of("at",Instant.now().toString())));}catch(IOException ex){clients.remove(e);}return e;}

    /** Per-turn subscription for agent SSE streams. */
    public SseEmitter subscribe(String turnId){SseEmitter e=new SseEmitter(0L);turnEmitters.computeIfAbsent(turnId,k->ConcurrentHashMap.newKeySet()).add(e);e.onCompletion(()->removeTurnEmitter(turnId,e));e.onTimeout(()->removeTurnEmitter(turnId,e));try{e.send(SseEmitter.event().name("ready").data(Map.of("at",Instant.now().toString(),"turnId",turnId)));}catch(IOException ex){removeTurnEmitter(turnId,e);}return e;}

    /** Publish to a specific turn's subscribers. */
    public void publishToTurn(String turnId,String event,Object data){Set<SseEmitter> emitters=turnEmitters.getOrDefault(turnId,Set.of());for(SseEmitter client:emitters)try{client.send(SseEmitter.event().name(event).data(data));}catch(Exception ex){removeTurnEmitter(turnId,client);client.complete();}}

    /** Global broadcast (existing behavior). */
    public void publish(String event,Object data){for(SseEmitter client:clients)try{client.send(SseEmitter.event().name(event).data(data));}catch(Exception ex){clients.remove(client);client.complete();}}

    private void removeTurnEmitter(String turnId,SseEmitter e){Set<SseEmitter> emitters=turnEmitters.get(turnId);if(emitters!=null){emitters.remove(e);if(emitters.isEmpty())turnEmitters.remove(turnId);}}
}

