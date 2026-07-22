package com.subtlesight.report;

import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.application.Ports.Publisher;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ReportService {
    private final IntelligenceRepository repository;private final ObjectMapper json;private final Clock clock;
    public ReportService(IntelligenceRepository repository,ObjectMapper json,Clock clock){this.repository=repository;this.json=json;this.clock=clock;}
    public ReportVersion create(UUID researchId,String type,String title){ResearchRun research=repository.findResearch(researchId).orElseThrow();List<Claim> claims=repository.researchClaims(researchId);List<Map<String,Object>> sections=new ArrayList<>();boolean verified=true;int citation=1;StringBuilder md=new StringBuilder("# ").append(title).append("\n\n");StringBuilder html=new StringBuilder("<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8' /><style>body{font-family:sans-serif;line-height:1.7;max-width:900px;margin:40px auto;color:#17202a}blockquote{border-left:3px solid #3867d6;padding-left:16px;color:#52606d}</style></head><body><h1>").append(escape(title)).append("</h1>");
        for(Claim claim:claims){List<Evidence> evidence=repository.claimEvidence(claim.id());if(claim.critical()&&evidence.isEmpty())verified=false;Map<String,Object> section=new LinkedHashMap<>();section.put("claim",claim.statement());section.put("status",claim.status().name());section.put("evidence",evidence);sections.add(section);md.append("## ").append(claim.statement()).append("\n\n");html.append("<h2>").append(escape(claim.statement())).append("</h2>");for(Evidence e:evidence){md.append("> ").append(e.exactQuote()).append(" [").append(citation).append("]\n\n");html.append("<blockquote>").append(escape(e.exactQuote())).append(" <sup>[").append(citation).append("]</sup></blockquote>");citation++;}}
        md.append("\n---\nResearch status: ").append(research.status());html.append("<hr/><p>Research status: ").append(research.status()).append("</p></body></html>");String content=write(Map.of("title",title,"type",type,"researchId",researchId.toString(),"sections",sections));String hash=Hashing.sha256(content+md);int version=(int)repository.listReports(1000).stream().filter(r->researchId.equals(r.researchRunId())&&r.reportType().equals(type)).count()+1;ReportVersion report=new ReportVersion(UUID.randomUUID(),researchId,type,version,title,content,md.toString(),html.toString(),hash,verified,clock.instant());repository.saveReport(report);repository.appendOutbox("report",report.id(),"report.created","{}",clock.instant());return report;}
    public byte[] export(UUID reportId,String format){ReportVersion report=repository.findReport(reportId).orElseThrow();return switch(format.toLowerCase()){case "markdown","md"->report.markdown().getBytes(StandardCharsets.UTF_8);case "html"->report.html().getBytes(StandardCharsets.UTF_8);case "json"->report.contentJson().getBytes(StandardCharsets.UTF_8);case "pdf"->pdf(report.html());default->throw new IllegalArgumentException("unsupported export format");};}
    public Publication publish(UUID reportId,String destinationId,Publisher publisher,boolean confirmed){if(!confirmed)throw new SecurityException("publishing requires explicit confirmation");ReportVersion report=repository.findReport(reportId).orElseThrow();if(!report.citationsVerified())throw new IllegalStateException("report citations are not verified");String key=report.id()+":"+destinationId;Optional<Publication> existing=repository.findPublicationByKey(key);if(existing.isPresent()&&existing.get().status()==PublicationStatus.PUBLISHED)return existing.get();Publication requested=existing.orElse(new Publication(UUID.randomUUID(),report.id(),destinationId,key,PublicationStatus.REQUESTED,null,null,null,clock.instant(),clock.instant()));repository.savePublication(requested);try{Publisher.PublishReceipt receipt=publisher.publish(report,destinationId,key);Publication done=new Publication(requested.id(),report.id(),destinationId,key,PublicationStatus.PUBLISHED,receipt.remoteId(),receipt.receiptJson(),null,requested.createdAt(),clock.instant());repository.savePublication(done);return done;}catch(RuntimeException unknown){Publication reconcile=new Publication(requested.id(),report.id(),destinationId,key,PublicationStatus.RECONCILE,null,null,"REMOTE_STATUS_UNKNOWN",requested.createdAt(),clock.instant());repository.savePublication(reconcile);return reconcile;}}
    public Publication reconcile(String key,Publisher publisher){Publication p=repository.findPublicationByKey(key).orElseThrow();var receipt=publisher.reconcile(p.destinationId(),key);if(receipt.isEmpty())return p;var r=receipt.get();Publication done=new Publication(p.id(),p.reportVersionId(),p.destinationId(),p.idempotencyKey(),PublicationStatus.PUBLISHED,r.remoteId(),r.receiptJson(),null,p.createdAt(),clock.instant());return repository.savePublication(done);}
    private byte[] pdf(String html){try(ByteArrayOutputStream out=new ByteArrayOutputStream()){new PdfRendererBuilder().withHtmlContent(html,null).toStream(out).run();return out.toByteArray();}catch(Exception e){throw new IllegalStateException("PDF rendering failed",e);}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
