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
    public SseEmitter subscribe(){SseEmitter e=new SseEmitter(0L);clients.add(e);e.onCompletion(()->clients.remove(e));e.onTimeout(()->clients.remove(e));try{e.send(SseEmitter.event().name("ready").data(Map.of("at",Instant.now().toString())));}catch(IOException ex){clients.remove(e);}return e;}
    public void publish(String event,Object data){for(SseEmitter client:clients)try{client.send(SseEmitter.event().name(event).data(data));}catch(Exception ex){clients.remove(client);client.complete();}}
}

