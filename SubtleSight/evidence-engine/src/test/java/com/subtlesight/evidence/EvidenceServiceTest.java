package com.subtlesight.evidence;

import com.subtlesight.domain.Models.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Hashing;
import java.time.Clock;
import java.util.Optional;

class EvidenceServiceTest {
    @Test void foldsSameFamilyAndDisclosesConflict(){Instant now=Instant.now();UUID doc=UUID.randomUUID();var support=new Evidence(UUID.randomUUID(),doc,"a",0,1,"x","a".repeat(64),"wire",EvidenceRelation.SUPPORTS,.9,now);var duplicate=new Evidence(UUID.randomUUID(),doc,"b",1,2,"x","a".repeat(64),"wire",EvidenceRelation.SUPPORTS,.8,now);var refute=new Evidence(UUID.randomUUID(),doc,"c",2,3,"x","a".repeat(64),"official",EvidenceRelation.REFUTES,1,now);var service=new EvidenceService(null,java.time.Clock.systemUTC());assertThat(service.evaluate(List.of(support,duplicate,refute))).isEqualTo(ClaimStatus.DISPUTED);}
    @Test void anchorsVerifiesRefreshesAndComputesResearchGate(){IntelligenceRepository repo=mock(IntelligenceRepository.class);Instant now=Instant.parse("2026-07-16T00:00:00Z");UUID docId=UUID.randomUUID(),run=UUID.randomUUID();String text="可信原文证据";var doc=new DocumentVersion(docId,UUID.randomUUID(),"T",null,now,"zh","https://x.test",text,Hashing.sha256(text),null,"v1",null,false,now);when(repo.findDocumentVersion(docId)).thenReturn(Optional.of(doc));when(repo.saveEvidence(any())).thenAnswer(i->i.getArgument(0));EvidenceService service=new EvidenceService(repo,Clock.fixed(now,java.time.ZoneOffset.UTC));Evidence anchored=service.anchor(docId,"原文",EvidenceRelation.SUPPORTS,"official",.9);assertThat(anchored.startOffset()).isEqualTo(2);assertThat(service.verifyLocator(anchored)).isTrue();assertThatThrownBy(()->service.anchor(docId,"缺失",EvidenceRelation.SUPPORTS,"x",.5)).isInstanceOf(IllegalArgumentException.class);Claim claim=new Claim(UUID.randomUUID(),run,"结论",ClaimStatus.UNVERIFIED,true,null,null,now);when(repo.claimEvidence(claim.id())).thenReturn(List.of(anchored));when(repo.saveClaim(any())).thenAnswer(i->i.getArgument(0));assertThat(service.refreshClaim(claim).status()).isEqualTo(ClaimStatus.SUPPORTED);when(repo.researchClaims(run)).thenReturn(List.of(claim));assertThat(service.verifyResearch(run).publishable()).isTrue();var bad=new Evidence(UUID.randomUUID(),docId,"错",0,1,"x",doc.textHash(),null,EvidenceRelation.QUALIFIES,.5,now);when(repo.claimEvidence(claim.id())).thenReturn(List.of(bad));assertThat(service.verifyResearch(run).publishable()).isFalse();assertThat(service.evaluate(List.of())).isEqualTo(ClaimStatus.UNVERIFIED);assertThat(service.evaluate(List.of(new Evidence(UUID.randomUUID(),docId,"x",0,1,"x",doc.textHash(),"f",EvidenceRelation.REFUTES,.5,now)))).isEqualTo(ClaimStatus.REFUTED);}
}
