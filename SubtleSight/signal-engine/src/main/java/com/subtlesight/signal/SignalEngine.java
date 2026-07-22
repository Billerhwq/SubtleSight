package com.subtlesight.signal;

import com.subtlesight.domain.Models.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SignalEngine {
    public record Context(double interestMatch,double impact,double novelty,double change,int mentionsLastHour,
                          int independentFamilies,double sourceQuality,int supportingFamilies,int refutingFamilies){}
    private final Clock clock;
    public SignalEngine(Clock clock){this.clock=clock;}
    public Signal project(Story story,ViewType view,UUID savedViewId,Context c){
        double velocity=clamp(Math.log1p(Math.max(0,c.mentionsLastHour()))/Math.log(21));
        double independence=clamp(c.independentFamilies()/5d);
        double confidence=clamp(.35*c.sourceQuality()+.65*Math.min(c.supportingFamilies(),4)/4d);
        double disagreement=c.supportingFamilies()+c.refutingFamilies()==0?0:2d*Math.min(c.supportingFamilies(),c.refutingFamilies())/(c.supportingFamilies()+c.refutingFamilies());
        double freshness=Math.exp(-Math.max(0,Duration.between(story.lastObservedAt(),clock.instant()).toHours())/72d);
        SignalFeatures f=new SignalFeatures(clamp(c.interestMatch()),clamp(c.impact()),clamp(c.novelty()),velocity,clamp(c.change()),clamp(c.sourceQuality()),independence,confidence,clamp(disagreement),clamp(freshness));
        double score=score(view,f);List<String> reasons=reasons(f);
        return new Signal(UUID.randomUUID(),story.id(),view,savedViewId,f,score,reasons,clock.instant());
    }
    double score(ViewType v,SignalFeatures f){return clamp(switch(v){
        case LATEST->.85*f.freshness()+.15*f.sourceQuality();
        case EMERGING->.35*f.velocity()+.25*f.independence()+.2*f.change()+.1*f.novelty()+.1*f.sourceQuality();
        case IMPORTANT->.4*f.impact()+.25*f.sourceQuality()+.2*f.confidence()+.1*f.independence()+.05*f.freshness();
        case FOR_YOU,SAVED->.3*f.relevance()+.2*f.impact()+.15*f.novelty()+.15*f.confidence()+.1*f.freshness()+.1*f.independence();});}
    private List<String> reasons(SignalFeatures f){List<String> r=new ArrayList<>();if(f.impact()>=.7)r.add("HIGH_IMPACT");if(f.velocity()>=.6)r.add("RAPID_GROWTH");if(f.independence()>=.6)r.add("MULTIPLE_INDEPENDENT_SOURCES");if(f.disagreement()>=.4)r.add("SOURCE_DISAGREEMENT");if(f.novelty()>=.7)r.add("NOVEL_INFORMATION");if(f.relevance()>=.7)r.add("MATCHES_YOUR_INTERESTS");if(r.isEmpty())r.add("RECENT_RELEVANT_UPDATE");return List.copyOf(r);}
    private static double clamp(double v){return Math.max(0,Math.min(1,Double.isFinite(v)?v:0));}
}
