package com.subtlesight.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.application.Ports.AiProvider;
import com.subtlesight.application.TraceableQaPorts.*;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.TraceableQa.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TraceableQaServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test void disabledAiFallsBackToVerifiedExtractiveAnswerAndResolvableCitation() {
        Fixture fixture = fixture(request -> new AiProvider.AiResult("{}", 0, 0, "disabled", "none"));
        TraceableQaService.StartResult started = fixture.service.start(null, null, "延期原因是什么？", fixture.scopes);
        QaAnswer completed = fixture.service.execute(started.answer().id());
        assertThat(completed.status()).isEqualTo(AnswerStatus.COMPLETED);
        assertThat(completed.directAnswer()).isEqualTo(fixture.unit.text());
        assertThat(fixture.savedClaim.get().verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(fixture.savedCitation.get().exactQuote()).isEqualTo(fixture.unit.text());
        assertThat(fixture.service.resolveCitation(fixture.savedCitation.get().id()).locatorValid()).isTrue();
    }

    @Test void hallucinatedQuoteIsBlockedAndCannotBecomeFact() {
        String hallucination = """
                {"claims":[{"type":"SOURCE_FACT","statement":"人员变动导致延期",\
                "evidence":[{"unit":1,"quote":"项目经理已经离职"}]}]}
                """;
        Fixture fixture = fixture(request -> new AiProvider.AiResult(hallucination, 20, 10, "fixture", "test"));
        QaAnswer answer = fixture.service.execute(fixture.service.start(null, null, "为什么延期？", fixture.scopes).answer().id());
        assertThat(answer.status()).isEqualTo(AnswerStatus.INSUFFICIENT);
        assertThat(answer.directAnswer()).isEqualTo("当前资料无法确认。");
        assertThat(fixture.savedClaim.get()).isNull();
        assertThat(answer.gaps()).anyMatch(value -> value.contains("拦截"));
    }

    private static Fixture fixture(AiProvider ai) {
        Repository repository = mock(Repository.class);
        LexicalUnitIndex lexical = mock(LexicalUnitIndex.class);
        SemanticUnitIndex semantic = mock(SemanticUnitIndex.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        UUID resourceId = UUID.randomUUID();
        KnowledgeUnit unit = new KnowledgeUnit(UUID.randomUUID(), ResourceType.DOCUMENT, resourceId, "1", null,
                UnitType.PARAGRAPH, "block:goal", "接口冻结时间推迟导致测试窗口压缩", "项目排期",
                "{\"blockId\":\"goal\"}", Hashing.sha256("接口冻结时间推迟导致测试窗口压缩"), NOW);
        List<ScopeRef> scopes = List.of(new ScopeRef(ScopeType.DOCUMENT, resourceId, null, List.of()));
        ResolvedScope resolved = new ResolvedScope(List.of(unit.versionKey()), List.of(unit.id()), List.of());
        AtomicReference<QaAnswer> stored = new AtomicReference<>();
        AtomicReference<QaClaim> claim = new AtomicReference<>();
        AtomicReference<QaCitation> citation = new AtomicReference<>();
        when(repository.resolveScope(anyList())).thenReturn(resolved);
        when(repository.saveConversation(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.conversation(any())).thenReturn(Optional.empty());
        when(repository.saveAnswer(any(), anyList(), anyString())).thenAnswer(call -> { QaAnswer value = call.getArgument(0); stored.set(value); return value; });
        when(repository.answer(any())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.answerUnitIds(any())).thenReturn(List.of(unit.id()));
        when(repository.answerScopeSnapshot(any())).thenReturn("{}");
        when(repository.unitsByIds(anyCollection())).thenReturn(List.of(unit));
        when(repository.unit(unit.id())).thenReturn(Optional.of(unit));
        when(repository.currentVersion(unit.resourceType(), unit.resourceId())).thenReturn(Optional.of("1"));
        when(repository.saveClaim(any())).thenAnswer(call -> { QaClaim value = call.getArgument(0); claim.set(value); return value; });
        when(repository.saveCitation(any())).thenAnswer(call -> { QaCitation value = call.getArgument(0); citation.set(value); return value; });
        when(repository.citation(any())).thenAnswer(call -> Optional.ofNullable(citation.get()));
        when(lexical.search(anyString(), anySet(), anyInt())).thenReturn(List.of(new UnitHit(unit.id(), 1, "LEXICAL")));
        when(semantic.search(any(), any(float[].class), anySet(), anyInt())).thenReturn(List.of(new UnitHit(unit.id(), 1, "SEMANTIC")));
        when(semantic.backend()).thenReturn("fixture");
        EmbeddingProfile profile = new EmbeddingProfile("test", "test", "test", "1", 2, "COSINE", 1, true, NOW);
        when(embeddings.profile()).thenReturn(profile);
        when(embeddings.embed(anyList())).thenReturn(List.of(new float[]{1, 0}));
        TraceableQaService service = new TraceableQaService(repository,
                new HybridKnowledgeRetriever(repository, lexical, semantic, embeddings), ai,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, unit, scopes, claim, citation);
    }

    private record Fixture(TraceableQaService service, KnowledgeUnit unit, List<ScopeRef> scopes,
                           AtomicReference<QaClaim> savedClaim, AtomicReference<QaCitation> savedCitation) {}
}
