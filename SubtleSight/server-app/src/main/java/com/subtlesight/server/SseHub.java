package com.subtlesight.server;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class SseHub {
    private final Set<SseEmitter> clients=ConcurrentHashMap.newKeySet();
    private final Map<String,Set<SseEmitter>> turnEmitters=new ConcurrentHashMap<>();
    private final Map<String,ReplayBuffer> turnReplay=new ConcurrentHashMap<>();

    /** Global subscription (existing behavior). */
    public SseEmitter subscribe(){SseEmitter e=new SseEmitter(0L);clients.add(e);e.onCompletion(()->clients.remove(e));e.onTimeout(()->clients.remove(e));try{e.send(SseEmitter.event().name("ready").data(Map.of("at",Instant.now().toString())));}catch(IOException ex){clients.remove(e);}return e;}

    /** Per-turn subscription for agent SSE streams. */
    public SseEmitter subscribe(String turnId){SseEmitter e=new SseEmitter(0L);turnEmitters.computeIfAbsent(turnId,k->ConcurrentHashMap.newKeySet()).add(e);e.onCompletion(()->removeTurnEmitter(turnId,e));e.onTimeout(()->removeTurnEmitter(turnId,e));try{e.send(SseEmitter.event().name("ready").data(Map.of("at",Instant.now().toString(),"turnId",turnId)));ReplayBuffer replay=turnReplay.get(turnId);if(replay!=null)for(ReplayEvent event:replay.snapshot())e.send(SseEmitter.event().name(event.name()).data(event.data()));}catch(IOException ex){removeTurnEmitter(turnId,e);}return e;}

    /** Publish to a specific turn's subscribers. */
    public void publishToTurn(String turnId,String event,Object data){boolean added=turnReplay.computeIfAbsent(turnId,key->new ReplayBuffer()).add(new ReplayEvent(event,data));if(!added)return;Set<SseEmitter> emitters=turnEmitters.getOrDefault(turnId,Set.of());for(SseEmitter client:emitters)try{client.send(SseEmitter.event().name(event).data(data));}catch(Exception ex){removeTurnEmitter(turnId,client);client.complete();}}

    /** Global broadcast (existing behavior). */
    public void publish(String event,Object data){for(SseEmitter client:clients)try{client.send(SseEmitter.event().name(event).data(data));}catch(Exception ex){clients.remove(client);client.complete();}}

    private void removeTurnEmitter(String turnId,SseEmitter e){Set<SseEmitter> emitters=turnEmitters.get(turnId);if(emitters!=null){emitters.remove(e);if(emitters.isEmpty())turnEmitters.remove(turnId);}}

    @Scheduled(fixedDelay=60_000)
    public void cleanupReplay(){Instant cutoff=Instant.now().minus(Duration.ofMinutes(15));turnReplay.entrySet().removeIf(entry->entry.getValue().lastUpdated().isBefore(cutoff));}

    private record ReplayEvent(String name,Object data){}

    private static final class ReplayBuffer{
        private static final int MAX_EVENTS=250;
        private final Deque<ReplayEvent> events=new ArrayDeque<>();
        private Instant lastUpdated=Instant.now();
        synchronized boolean add(ReplayEvent event){lastUpdated=Instant.now();if(events.contains(event))return false;events.addLast(event);while(events.size()>MAX_EVENTS)events.removeFirst();return true;}
        synchronized java.util.List<ReplayEvent> snapshot(){lastUpdated=Instant.now();return new ArrayList<>(events);}
        synchronized Instant lastUpdated(){return lastUpdated;}
    }
}

