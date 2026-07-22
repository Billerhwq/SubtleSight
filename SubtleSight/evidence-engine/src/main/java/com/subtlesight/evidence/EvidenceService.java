package com.subtlesight.evidence;

import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.*;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EvidenceService {
    private final IntelligenceRepository repository;
    private final Clock clock;
    public EvidenceService(IntelligenceRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    public Evidence anchor(UUID documentId,String quote,EvidenceRelation relation,String sourceFamily,double quality){
        DocumentVersion document=repository.findDocumentVersion(documentId).orElseThrow();int start=document.text().indexOf(quote);
        if(start<0)throw new IllegalArgumentException("exact quote does not exist in snapshot");
        Evidence evidence=new Evidence(UUID.randomUUID(),documentId,quote,start,start+quote.length(),"text="+start+":"+(start+quote.length()),
                document.textHash(),sourceFamily,relation,quality,clock.instant());return repository.saveEvidence(evidence);
    }
    public boolean verifyLocator(Evidence evidence){return repository.findDocumentVersion(evidence.documentVersionId()).map(d->
            d.textHash().equals(evidence.snapshotHash())&&evidence.startOffset()>=0&&evidence.endOffset()<=d.text().length()
                    &&d.text().substring(evidence.startOffset(),evidence.endOffset()).equals(evidence.exactQuote())).orElse(false);}
    public ClaimStatus evaluate(List<Evidence> evidence){
        Set<String> supports=new HashSet<>(),refutes=new HashSet<>();
        for(Evidence e:evidence){String family=e.sourceFamily()==null?"unknown:"+e.id():e.sourceFamily();if(e.relation()==EvidenceRelation.SUPPORTS)supports.add(family);if(e.relation()==EvidenceRelation.REFUTES)refutes.add(family);}
        if(!supports.isEmpty()&&!refutes.isEmpty())return ClaimStatus.DISPUTED;if(!refutes.isEmpty())return ClaimStatus.REFUTED;if(!supports.isEmpty())return ClaimStatus.SUPPORTED;return ClaimStatus.UNVERIFIED;
    }
    public Claim refreshClaim(Claim claim){List<Evidence> evidence=repository.claimEvidence(claim.id());Claim updated=new Claim(claim.id(),claim.researchRunId(),claim.statement(),evaluate(evidence),claim.critical(),claim.validFrom(),claim.validTo(),claim.createdAt());return repository.saveClaim(updated);}
    public VerificationResult verifyResearch(UUID researchId){List<Claim> claims=repository.researchClaims(researchId);int critical=0,covered=0,located=0,total=0;for(Claim c:claims){if(c.critical())critical++;List<Evidence> evidence=repository.claimEvidence(c.id());if(!evidence.isEmpty()&&c.critical())covered++;for(Evidence e:evidence){total++;if(verifyLocator(e))located++;}}
        double coverage=critical==0?1:(double)covered/critical;double locator=total==0?0:(double)located/total;return new VerificationResult(coverage,locator,coverage>=.95&&locator>=.995,critical,total);}
    public record VerificationResult(double citationCoverage,double locatorRate,boolean publishable,int criticalClaims,int evidenceCount){}
}

