package com.subtlesight.storage.sqlite;

import com.subtlesight.domain.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteRepositoryFunctionalTest {
    @TempDir Path temp;
    @Test void migratesRealSqliteAndPersistsSourceAndRawDedup(){var ds=SqliteDataSourceFactory.create(temp.resolve("test.db"));var repo=new SqliteIntelligenceRepository(ds,new ObjectMapper());Instant now=Instant.now();Source source=new Source(UUID.randomUUID(),"source",SourceType.RSS,SourceKind.PERSISTENT,"https://example.com/rss","0 0 * * * *",null,SourceTier.PROFESSIONAL,SourceHealth.HEALTHY,Set.of("AI"),true,1,now,now);repo.saveSource(source);assertThat(repo.findSource(source.id())).contains(source);RawDocument raw=new RawDocument(UUID.randomUUID(),source.id(),null,null,"https://example.com/a","https://example.com/a","text/plain","UTF-8","a".repeat(64),"b".repeat(64),1,200,"test",DocumentStatus.STORED,now,now);repo.saveRawDocument(raw);RawDocument replay=new RawDocument(UUID.randomUUID(),source.id(),null,null,"https://other.example/a","https://other.example/a","text/plain","UTF-8","a".repeat(64),"b".repeat(64),1,200,"test",DocumentStatus.STORED,now,now);assertThat(repo.saveRawDocument(replay).id()).isEqualTo(raw.id());assertThat(repo.count("raw_documents")).isEqualTo(1);}
}
