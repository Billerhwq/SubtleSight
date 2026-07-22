package com.subtlesight.watchlist;

import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Models.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WatchlistService {
    private static final TypeReference<Map<String,Object>> MAP=new TypeReference<>(){};
    private final IntelligenceRepository repository;private final ObjectMapper json;private final Clock clock;
    public WatchlistService(IntelligenceRepository repository,ObjectMapper json,Clock clock){this.repository=repository;this.json=json;this.clock=clock;}
    public WatchTarget create(WatchType type,String name,String expression,Map<String,Object> baseline){Instant now=clock.instant();WatchTarget t=new WatchTarget(UUID.randomUUID(),type,name,expression,write(baseline),1,true,null,now,now);repository.saveWatchTarget(t);repository.appendAudit("user","watch.create","watch_target",t.id().toString(),"{}",now);return t;}
    public List<ChangeEvent> detect(UUID targetId,Map<String,Object> current,String source){WatchTarget target=repository.findWatchTarget(targetId).orElseThrow();if(!target.enabled())return List.of();Map<String,Object> baseline=read(target.baselineJson());java.util.List<ChangeEvent> changes=new java.util.ArrayList<>();for(String field:union(baseline,current)){Object old=baseline.get(field),next=current.get(field);if(equivalent(old,next))continue;ChangeSeverity severity=severity(field,old,next);double confidence=structured(old,next)?1:.75;ChangeEvent event=new ChangeEvent(UUID.randomUUID(),targetId,field,string(old),string(next),source,"FIELD_CHANGE",confidence,severity,"NEW",clock.instant());repository.saveChange(event);repository.appendOutbox("watch",targetId,"watch.change_detected",write(Map.of("changeId",event.id().toString(),"severity",severity.name())),clock.instant());changes.add(event);}return List.copyOf(changes);}
    public WatchTarget confirm(UUID targetId,Map<String,Object> newBaseline,Duration cooldown){WatchTarget old=repository.findWatchTarget(targetId).orElseThrow();Instant now=clock.instant();WatchTarget updated=new WatchTarget(old.id(),old.type(),old.name(),old.expression(),write(newBaseline),old.baselineVersion()+1,old.enabled(),now.plus(cooldown),old.createdAt(),now);return repository.saveWatchTarget(updated);}
    public static double ewma(double previous,double value,double alpha){if(alpha<=0||alpha>1)throw new IllegalArgumentException("alpha must be in (0,1]");return alpha*value+(1-alpha)*previous;}
    public static double zScore(double value,double mean,double standardDeviation){return standardDeviation==0?0:(value-mean)/standardDeviation;}
    private ChangeSeverity severity(String field,Object old,Object value){String f=field.toLowerCase();if(f.matches(".*(status|policy|security|price|version|release).*"))return ChangeSeverity.HIGH;if(old==null||value==null)return ChangeSeverity.MEDIUM;if(old instanceof Number a&&value instanceof Number b){double denominator=Math.max(Math.abs(a.doubleValue()),1);if(Math.abs(b.doubleValue()-a.doubleValue())/denominator>=.5)return ChangeSeverity.HIGH;}return ChangeSeverity.LOW;}
    private boolean equivalent(Object a,Object b){if(Objects.equals(a,b))return true;if(a instanceof Number x&&b instanceof Number y)return Math.abs(x.doubleValue()-y.doubleValue())<1e-9;return false;}
    private boolean structured(Object a,Object b){return a==null||b==null||a instanceof Number||b instanceof Number||a instanceof Boolean||b instanceof Boolean;}
    private java.util.Set<String> union(Map<String,Object>a,Map<String,Object>b){java.util.Set<String>s=new java.util.LinkedHashSet<>(a.keySet());s.addAll(b.keySet());return s;}
    private String string(Object v){return v==null?null:String.valueOf(v);}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private Map<String,Object> read(String v){if(v==null||v.isBlank())return Map.of();try{return json.readValue(v,MAP);}catch(Exception e){throw new IllegalArgumentException(e);}}
}

