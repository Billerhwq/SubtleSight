package com.subtlesight.storage.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.TraceableQa.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteTraceableQaRepositoryTest {
    @TempDir Path temp;

    @Test void migratesPersistsResolvesRecursiveScopeAndStoresAnswerGraph() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        var dataSource = SqliteDataSourceFactory.create(temp.resolve("qa.db"));
        var knowledge = new SqliteKnowledgeRepository(dataSource);
        var repository = new SqliteTraceableQaRepository(dataSource, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
        UUID parent = UUID.randomUUID(), child = UUID.randomUUID(), fileId = UUID.randomUUID();
        knowledge.insertFolder(new SqliteKnowledgeRepository.KnowledgeFolder(parent, null, "研究", now, now));
        knowledge.insertFolder(new SqliteKnowledgeRepository.KnowledgeFolder(child, parent, "子目录", now, now));
        knowledge.insertFile(new SqliteKnowledgeRepository.KnowledgeFile(fileId, child, "需求.txt", "txt", "text/plain",
                12, "a".repeat(64), "file.txt", now, now));
        ResourceVersionKey version = new ResourceVersionKey(ResourceType.FILE, fileId, "a".repeat(64));
        KnowledgeUnit unit = new KnowledgeUnit(UUID.randomUUID(), ResourceType.FILE, fileId, version.version(), child,
                UnitType.SECTION, "chunk:0001", "接口冻结时间推迟", "项目排期", "{\"ordinal\":1}",
                Hashing.sha256("接口冻结时间推迟"), now);
        repository.upsertUnits(version, List.of(unit));
        repository.markIndexState(version, IndexStatus.READY, null, 1);

        ResolvedScope scope = repository.resolveScope(List.of(new ScopeRef(ScopeType.FOLDER, parent, null, List.of())));
        assertThat(scope.unitIds()).containsExactly(unit.id());
        assertThat(scope.resources()).containsExactly(version);

        QaConversation conversation = repository.saveConversation(new QaConversation(UUID.randomUUID(), "延期原因", now, now));
        QaAnswer answer = new QaAnswer(UUID.randomUUID(), conversation.id(), null, "为什么延期", AnswerStatus.COMPLETED,
                "接口冻结时间推迟", List.of(), "local", "extractive", null, now, now);
        repository.saveAnswer(answer, scope.unitIds(), "{\"frozen\":true}");
        QaClaim claim = repository.saveClaim(new QaClaim(UUID.randomUUID(), answer.id(), 0, ClaimType.SOURCE_FACT,
                "接口冻结时间推迟", VerificationStatus.VERIFIED));
        QaCitation citation = repository.saveCitation(new QaCitation(UUID.randomUUID(), claim.id(), CitationRelation.SUPPORTS,
                ResourceType.FILE, fileId, version.version(), unit.id(), "{\"stableLocator\":\"chunk:0001\"}",
                unit.text(), unit.contentHash(), "FILE:" + fileId, 0, now));

        assertThat(repository.answer(answer.id())).contains(answer);
        assertThat(repository.answerUnitIds(answer.id())).containsExactly(unit.id());
        assertThat(repository.claim(claim.id())).contains(claim);
        assertThat(repository.citationsForClaim(claim.id())).containsExactly(citation);
        assertThat(repository.currentVersion(ResourceType.FILE, fileId)).contains(version.version());
    }
}
