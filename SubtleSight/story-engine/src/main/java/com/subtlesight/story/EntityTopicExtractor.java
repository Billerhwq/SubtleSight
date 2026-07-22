package com.subtlesight.story;

import com.subtlesight.domain.Models.DocumentVersion;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Structured metadata and deterministic dictionaries run before any optional model disambiguation. */
public final class EntityTopicExtractor {
    private static final Pattern GITHUB = Pattern.compile("github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL = Pattern.compile("(?i)\\b(GPT-[0-9A-Za-z.-]+|Claude(?: (?:[0-9][0-9A-Za-z.-]*|Opus|Sonnet|Haiku))?|Gemini(?: [0-9][0-9A-Za-z.-]*)?|DeepSeek(?:-[A-Za-z0-9.-]+)?)\\b");
    public record Result(Set<String> entities,Set<String> topics){}
    public Result extract(DocumentVersion doc,Set<String> configuredTopics){
        Set<String> entities=new LinkedHashSet<>(),topics=new LinkedHashSet<>(); String content=doc.title()+" "+doc.text();
        var gh=GITHUB.matcher(content);while(gh.find())entities.add("repo:"+gh.group(1).toLowerCase(Locale.ROOT));
        var model=MODEL.matcher(content);while(model.find())entities.add("model:"+model.group(1).trim());
        if(doc.canonicalUrl()!=null)try{String host=URI.create(doc.canonicalUrl()).getHost();if(host!=null)entities.add("domain:"+host.toLowerCase(Locale.ROOT));}catch(Exception ignored){}
        for(String topic:configuredTopics)if(content.toLowerCase(Locale.ROOT).contains(topic.toLowerCase(Locale.ROOT)))topics.add(topic);
        return new Result(Set.copyOf(entities),Set.copyOf(topics));
    }
}
