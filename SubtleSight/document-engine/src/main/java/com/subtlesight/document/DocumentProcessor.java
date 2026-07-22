package com.subtlesight.document;

import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.DocumentVersion;
import com.subtlesight.domain.Models.RawDocument;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DocumentProcessor {
    private static final Set<String> INJECTION_MARKERS = Set.of("ignore previous instructions", "system prompt", "调用工具", "忽略之前的指令", "developer message");
    private final Clock clock;
    private final Tika tika = new Tika();
    public DocumentProcessor(Clock clock) { this.clock=clock; }
    public DocumentVersion process(RawDocument raw, byte[] content) {
        Extracted e = isHtml(raw.mimeType()) ? extractHtml(raw.canonicalUrl(), content) : extractDocument(content, raw.mimeType());
        String clean = normalize(e.text());
        if (clean.isBlank()) throw new IllegalArgumentException("document contains no extractable text");
        return new DocumentVersion(UUID.randomUUID(), raw.id(), nonBlank(e.title(), fallbackTitle(raw.canonicalUrl())), e.author(), e.publishedAt(),
                language(clean), e.canonicalUrl()==null?raw.canonicalUrl():e.canonicalUrl(), clean, Hashing.sha256(clean), null,
                "document-engine/1.0", null, containsInjection(clean), clock.instant());
    }
    private Extracted extractHtml(String base, byte[] bytes) {
        Document doc=Jsoup.parse(new String(bytes,StandardCharsets.UTF_8),base);
        doc.select("script,style,noscript,nav,footer,header,aside,form,svg").remove();
        String title=first(doc.select("meta[property=og:title]").attr("content"),doc.title(),doc.select("h1").first()==null?null:doc.select("h1").first().text());
        String author=first(doc.select("meta[name=author]").attr("content"),doc.select("[rel=author]").text());
        String publishedRaw=first(doc.select("meta[property=article:published_time]").attr("content"),doc.select("time[datetime]").attr("datetime"));
        Instant published=parseInstant(publishedRaw);
        String canonical=doc.select("link[rel=canonical]").attr("abs:href");
        String text=doc.select("article,main").text(); if(text.isBlank()) text=doc.body().text();
        return new Extracted(title,author,published,canonical.isBlank()?base:canonical,text);
    }
    private Extracted extractDocument(byte[] bytes,String mediaType){ try { String text=tika.parseToString(new ByteArrayInputStream(bytes)); return new Extracted(null,null,null,null,text); } catch(Exception ex){ throw new IllegalArgumentException("document parse failed",ex); } }
    public java.util.List<String> chunk(String text,int maxChars,int overlap){ if(maxChars<100||overlap<0||overlap>=maxChars) throw new IllegalArgumentException("invalid chunk settings");
        java.util.List<String> chunks=new java.util.ArrayList<>(); int start=0; while(start<text.length()){ int end=Math.min(text.length(),start+maxChars); if(end<text.length()){ int boundary=Math.max(text.lastIndexOf('。',end),Math.max(text.lastIndexOf('.',end),text.lastIndexOf('\n',end))); if(boundary>start+maxChars/2) end=boundary+1; }
            chunks.add(text.substring(start,end)); if(end==text.length()) break; start=end-overlap; } return List.copyOf(chunks); }
    public boolean containsInjection(String text){ String value=text.toLowerCase(Locale.ROOT); return INJECTION_MARKERS.stream().anyMatch(value::contains); }
    private static String normalize(String s){ return s==null?"":s.replace('\u00a0',' ').replaceAll("[\\t\\x0B\\f\\r ]+"," ").replaceAll("\\n{3,}","\n\n").trim(); }
    private static boolean isHtml(String mime){ return mime!=null&&mime.toLowerCase(Locale.ROOT).contains("html"); }
    private static String language(String text){ long cjk=text.codePoints().filter(c->c>=0x4e00&&c<=0x9fff).count(); return cjk>Math.max(3,text.length()/20)?"zh":"en"; }
    private static String fallbackTitle(String url){ try{ String path=java.net.URI.create(url).getPath(); return path==null||path.equals("/")?java.net.URI.create(url).getHost():path.substring(path.lastIndexOf('/')+1); }catch(Exception e){return "Untitled";} }
    private static String nonBlank(String a,String b){return a==null||a.isBlank()?b:a;}
    private static String first(String... values){ for(String v:values)if(v!=null&&!v.isBlank())return v; return null; }
    private static Instant parseInstant(String value){ if(value==null||value.isBlank())return null; try{return Instant.parse(value);}catch(DateTimeParseException e){return null;} }
    private record Extracted(String title,String author,Instant publishedAt,String canonicalUrl,String text){}
}
