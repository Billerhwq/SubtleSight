package com.subtlesight.storage.sqlite;

import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;

public final class SqliteDataSourceFactory {
    private SqliteDataSourceFactory() {}

    public static DataSource create(Path database) {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(10_000);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + database.toAbsolutePath());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return dataSource;
    }
}

