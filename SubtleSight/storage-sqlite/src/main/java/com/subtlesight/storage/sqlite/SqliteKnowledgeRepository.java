package com.subtlesight.storage.sqlite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteKnowledgeRepository {
    private final JdbcTemplate jdbc;

    public SqliteKnowledgeRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record KnowledgeFolder(UUID id, UUID parentId, String name, Instant createdAt, Instant updatedAt) {}

    public record KnowledgeFile(UUID id, UUID folderId, String name, String ext, String mimeType,
                                long sizeBytes, String sha256, String storagePath,
                                Instant createdAt, Instant updatedAt) {}

    private static final RowMapper<KnowledgeFolder> FOLDER_MAPPER = (rs, n) -> new KnowledgeFolder(
            UUID.fromString(rs.getString("id")),
            rs.getString("parent_id") == null ? null : UUID.fromString(rs.getString("parent_id")),
            rs.getString("name"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at")));

    private static final RowMapper<KnowledgeFile> FILE_MAPPER = (rs, n) -> new KnowledgeFile(
            UUID.fromString(rs.getString("id")),
            rs.getString("folder_id") == null ? null : UUID.fromString(rs.getString("folder_id")),
            rs.getString("name"),
            rs.getString("ext"),
            rs.getString("mime_type"),
            rs.getLong("size_bytes"),
            rs.getString("sha256"),
            rs.getString("storage_path"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at")));

    public List<KnowledgeFolder> listFolders() {
        return jdbc.query("SELECT * FROM knowledge_folders ORDER BY name", FOLDER_MAPPER);
    }

    public boolean folderExists(UUID id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_folders WHERE id=?", Integer.class, id.toString());
        return count != null && count > 0;
    }

    public void insertFolder(KnowledgeFolder folder) {
        jdbc.update("INSERT INTO knowledge_folders(id,parent_id,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                folder.id().toString(),
                folder.parentId() == null ? null : folder.parentId().toString(),
                folder.name(), folder.createdAt().toString(), folder.updatedAt().toString());
    }

    public List<KnowledgeFile> listFiles(UUID folderId) {
        if (folderId == null)
            return jdbc.query("SELECT * FROM knowledge_files WHERE folder_id IS NULL ORDER BY name", FILE_MAPPER);
        return jdbc.query("SELECT * FROM knowledge_files WHERE folder_id=? ORDER BY name", FILE_MAPPER, folderId.toString());
    }

    public Optional<KnowledgeFile> findFile(UUID id) {
        List<KnowledgeFile> rows = jdbc.query("SELECT * FROM knowledge_files WHERE id=?", FILE_MAPPER, id.toString());
        return rows.stream().findFirst();
    }

    public void insertFile(KnowledgeFile file) {
        jdbc.update("INSERT INTO knowledge_files(id,folder_id,name,ext,mime_type,size_bytes,sha256,storage_path,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                file.id().toString(),
                file.folderId() == null ? null : file.folderId().toString(),
                file.name(), file.ext(), file.mimeType(), file.sizeBytes(), file.sha256(),
                file.storagePath(), file.createdAt().toString(), file.updatedAt().toString());
    }

    public void renameFile(UUID id, String name, String ext, Instant updatedAt) {
        jdbc.update("UPDATE knowledge_files SET name=?, ext=?, updated_at=? WHERE id=?",
                name, ext, updatedAt.toString(), id.toString());
    }

    public void moveFile(UUID id, UUID folderId, Instant updatedAt) {
        jdbc.update("UPDATE knowledge_files SET folder_id=?, updated_at=? WHERE id=?",
                folderId == null ? null : folderId.toString(), updatedAt.toString(), id.toString());
    }

    public void deleteFile(UUID id) {
        jdbc.update("DELETE FROM knowledge_files WHERE id=?", id.toString());
    }

    /** 删除文件夹记录 */
    public void deleteFolder(UUID id) {
        jdbc.update("DELETE FROM knowledge_folders WHERE id=?", id.toString());
    }

    /** 查询指定父文件夹下的所有子文件夹 ID */
    public List<UUID> listSubfolderIds(UUID parentId) {
        return jdbc.query("SELECT id FROM knowledge_folders WHERE parent_id=?",
                (rs, n) -> UUID.fromString(rs.getString("id")), parentId.toString());
    }

    /** 查询指定文件夹下的所有文件 ID */
    public List<UUID> listFileIdsByFolder(UUID folderId) {
        return jdbc.query("SELECT id FROM knowledge_files WHERE folder_id=?",
                (rs, n) -> UUID.fromString(rs.getString("id")), folderId.toString());
    }

    /** 按名称模糊搜索文件夹（忽略大小写） */
    public List<KnowledgeFolder> searchFolders(String keyword) {
        return jdbc.query("SELECT * FROM knowledge_folders WHERE name LIKE ? ORDER BY name",
                FOLDER_MAPPER, "%" + keyword + "%");
    }

    /** 按名称模糊搜索文件（忽略大小写） */
    public List<KnowledgeFile> searchFiles(String keyword) {
        return jdbc.query("SELECT * FROM knowledge_files WHERE name LIKE ? ORDER BY name",
                FILE_MAPPER, "%" + keyword + "%");
    }
}
