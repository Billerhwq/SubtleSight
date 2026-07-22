package com.subtlesight.storage.blob;

import com.subtlesight.application.Ports.BlobStore;
import com.subtlesight.domain.Hashing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

public final class ContentAddressedBlobStore implements BlobStore {
    private final Path root;
    public ContentAddressedBlobStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try { Files.createDirectories(this.root.resolve("tmp")); }
        catch (IOException ex) { throw new IllegalStateException("cannot initialize blob store", ex); }
    }

    @Override public BlobRef put(byte[] bytes, String mediaType) {
        return put(new ByteArrayInputStream(bytes), mediaType, Math.max(bytes.length, 1));
    }

    @Override public BlobRef put(InputStream input, String mediaType, long maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("blob exceeds configured limit");
                output.write(buffer, 0, read);
            }
            byte[] content = output.toByteArray();
            String hash = Hashing.sha256(content);
            Path target = path(hash);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Path temp = root.resolve("tmp").resolve(UUID.randomUUID() + ".part");
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    channel.write(ByteBuffer.wrap(content));
                    channel.force(true);
                }
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target); }
                catch (java.nio.file.FileAlreadyExistsException ignored) { Files.deleteIfExists(temp); }
            }
            return new BlobRef(hash, content.length, mediaType == null ? "application/octet-stream" : mediaType);
        } catch (IOException ex) { throw new IllegalStateException("blob write failed", ex); }
    }

    @Override public Optional<InputStream> open(String hash) {
        validateHash(hash);
        Path path = path(hash);
        if (!Files.exists(path)) return Optional.empty();
        try { return Optional.of(Files.newInputStream(path, StandardOpenOption.READ)); }
        catch (IOException ex) { throw new IllegalStateException("blob read failed", ex); }
    }
    @Override public boolean exists(String hash) { validateHash(hash); return Files.exists(path(hash)); }
    @Override public void verify(String hash) {
        try (InputStream input = open(hash).orElseThrow(() -> new IllegalStateException("blob is missing"))) {
            String actual = Hashing.sha256(input.readAllBytes());
            if (!actual.equals(hash)) throw new IllegalStateException("blob checksum mismatch");
        } catch (IOException ex) { throw new IllegalStateException("blob verification failed", ex); }
    }
    public int cleanupTemporaryFiles() {
        try (var paths = Files.list(root.resolve("tmp"))) {
            int[] count = {0};
            paths.forEach(path -> { try { if (Files.deleteIfExists(path)) count[0]++; } catch (IOException ignored) {} });
            return count[0];
        } catch (IOException ex) { throw new IllegalStateException("temporary cleanup failed", ex); }
    }
    private Path path(String hash) { validateHash(hash); return root.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash); }
    private static void validateHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid SHA-256 hash");
    }
}

