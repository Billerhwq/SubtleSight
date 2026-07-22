package com.subtlesight.observability;

import com.subtlesight.domain.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BackupServiceTest {
    @TempDir Path temp;

    @Test void restoresBlobsWhenDataDirectoryContainsTmpAndSkipsOnlyBlobTempFiles() throws Exception {
        Path data = temp.resolve("tmp-named-data");
        Files.createDirectories(data.resolve("blobs/tmp"));
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + data.resolve("subtlesight.db"));
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            s.execute("create table marker(id text primary key)");
            s.execute("insert into marker(id) values('ok')");
        }

        byte[] body = "restorable evidence blob".getBytes(StandardCharsets.UTF_8);
        String hash = Hashing.sha256(body);
        Path blob = data.resolve("blobs").resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash);
        Files.createDirectories(blob.getParent());
        Files.write(blob, body);
        Files.write(data.resolve("blobs/tmp/ignored.part"), "partial".getBytes(StandardCharsets.UTF_8));

        BackupService service = new BackupService(ds, data, new ObjectMapper(), Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC));
        Path pack = data.resolve("backups/full.insightpack");
        service.create(pack);
        Path restored = temp.resolve("restored");
        service.restore(pack, restored);

        assertThat(restored.resolve("blobs").resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash)).hasBinaryContent(body);
        assertThat(restored.resolve("blobs/tmp/ignored.part")).doesNotExist();
        SQLiteDataSource restoredDs = new SQLiteDataSource();
        restoredDs.setUrl("jdbc:sqlite:" + restored.resolve("subtlesight.db"));
        try (var c = restoredDs.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("select count(*) from marker")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
