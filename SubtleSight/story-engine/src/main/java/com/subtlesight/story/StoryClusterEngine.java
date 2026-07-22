package com.subtlesight.story;

import com.subtlesight.domain.Models.DocumentVersion;
import com.subtlesight.domain.Models.Story;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class StoryClusterEngine {
    public enum Decision { EXACT_DUPLICATE, SAME_STORY, NEW_STORY, MANUAL_BLOCK }
    public record Candidate(Story story, DocumentVersion representative, boolean manualSplit) {}
    public record Result(Decision decision, Story story, double similarity, String reason) {}

    public Result decide(DocumentVersion incoming, java.util.List<Candidate> candidates) {
        Result best = new Result(Decision.NEW_STORY, null, 0, "NO_MATCH");
        for (Candidate candidate : candidates) {
            if (candidate.manualSplit()) continue;
            DocumentVersion existing = candidate.representative();
            if (existing.textHash().equals(incoming.textHash())) return new Result(Decision.EXACT_DUPLICATE, candidate.story(), 1, "TEXT_HASH");
            double similarity = similarity(existing.text(), incoming.text());
            boolean withinWindow = withinWindow(existing, incoming, Duration.ofDays(30));
            boolean contradiction = opposingPolarity(existing.text(), incoming.text());
            boolean versionConflict = versionConflict(existing.text(), incoming.text());
            double threshold = contradiction || versionConflict ? .94 : .72;
            if (withinWindow && similarity >= threshold && similarity > best.similarity())
                best = new Result(Decision.SAME_STORY, candidate.story(), similarity, contradiction ? "HIGH_SIMILARITY_OPPOSING_POLARITY" : "CONTENT_ENTITY_TIME");
        }
        return best;
    }

    public double similarity(String left, String right) {
        Set<String> a = shingles(left), b = shingles(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        double jaccard = (double) intersection.size() / union.size();
        long x = simHash(left) ^ simHash(right);
        double simHash = 1d - Long.bitCount(x) / 64d;
        return .55 * jaccard + .45 * simHash;
    }
    public long simHash(String text) {
        int[] weights = new int[64];
        for (String token : tokens(text)) {
            long hash = fnv1a(token);
            for (int i=0;i<64;i++) weights[i] += ((hash >>> i)&1)==1 ? 1 : -1;
        }
        long result=0; for(int i=0;i<64;i++) if(weights[i]>=0) result|=1L<<i; return result;
    }
    private Set<String> shingles(String text) {
        String normalized = normalize(text); Set<String> set=new HashSet<>();
        if(normalized.isEmpty()) return set;
        for(int i=0;i<Math.max(1,normalized.length()-4);i++) set.add(normalized.substring(i,Math.min(normalized.length(),i+5)));
        return set;
    }
    private String[] tokens(String text){ return normalize(text).split("(?U)[^\\p{L}\\p{N}]+|(?<=\\p{IsHan})(?=\\p{IsHan})"); }
    private String normalize(String text){ return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim(); }
    private long fnv1a(String value){ long h=0xcbf29ce484222325L; for(byte b:value.getBytes(java.nio.charset.StandardCharsets.UTF_8)){h^=b&0xff;h*=0x100000001b3L;}return h; }
    private boolean withinWindow(DocumentVersion a,DocumentVersion b,Duration duration){ if(a.publishedAt()==null||b.publishedAt()==null)return true; return Duration.between(a.publishedAt(),b.publishedAt()).abs().compareTo(duration)<=0; }
    private boolean opposingPolarity(String a,String b){ Set<String> positive=Set.of("发布","通过","支持","approved","released","supports"); Set<String> negative=Set.of("撤回","拒绝","反对","retracted","rejects","opposes");
        String x=normalize(a),y=normalize(b); return (contains(x,positive)&&contains(y,negative))||(contains(y,positive)&&contains(x,negative)); }
    private boolean versionConflict(String a,String b){ java.util.regex.Pattern p=java.util.regex.Pattern.compile("(?i)\\bv?([0-9]+(?:\\.[0-9]+){0,2})\\b"); var ma=p.matcher(a);var mb=p.matcher(b);return ma.find()&&mb.find()&&!ma.group(1).equals(mb.group(1)); }
    private boolean contains(String text,Set<String> words){return words.stream().anyMatch(text::contains);}
}
