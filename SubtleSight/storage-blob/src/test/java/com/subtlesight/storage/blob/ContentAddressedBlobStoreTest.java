package com.subtlesight.storage.blob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentAddressedBlobStoreTest {
    @TempDir Path temp;
    @Test void contentIsAddressedDeduplicatedAndVerified() throws Exception {
        var store = new ContentAddressedBlobStore(temp);
        var first = store.put("See the subtle".getBytes(StandardCharsets.UTF_8), "text/plain");
        var second = store.put("See the subtle".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(second.hash()).isEqualTo(first.hash());
        assertThat(store.open(first.hash()).orElseThrow().readAllBytes()).isEqualTo("See the subtle".getBytes(StandardCharsets.UTF_8));
        store.verify(first.hash());
    }
    @Test void enforcesHardSizeLimit() {
        var store = new ContentAddressedBlobStore(temp);
        assertThatThrownBy(() -> store.put(new java.io.ByteArrayInputStream(new byte[10]), "x", 9))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
