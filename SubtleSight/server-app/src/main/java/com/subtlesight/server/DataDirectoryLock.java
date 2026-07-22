package com.subtlesight.server;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DataDirectoryLock implements AutoCloseable {
    private final FileChannel channel;private final FileLock lock;
    public DataDirectoryLock(Path dataDir){try{Files.createDirectories(dataDir);channel=FileChannel.open(dataDir.resolve("subtlesight.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);lock=channel.tryLock();if(lock==null){channel.close();throw new IllegalStateException("another SubtleSight instance already owns this data directory");}}catch(IOException ex){throw new IllegalStateException("cannot lock data directory",ex);}}
    @Override public void close(){try{lock.release();channel.close();}catch(IOException ignored){}}
}
