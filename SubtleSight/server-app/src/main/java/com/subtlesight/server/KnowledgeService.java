package com.subtlesight.server;

import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFile;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

public class KnowledgeService {
    private static final int COPY_BUFFER_SIZE = 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 200;

    private final SqliteKnowledgeRepository repository;
    private final Path storageDir;
    private final Clock clock;

    public KnowledgeService(SqliteKnowledgeRepository repository, Path storageDir, Clock clock) {
        this.repository = repository;
        this.storageDir = storageDir;
        this.clock = clock;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot initialize knowledge storage directory", e);
        }
    }

    public List<KnowledgeFolder> folders() {
        return repository.listFolders();
    }

    public KnowledgeFolder createFolder(UUID parentId, String name) {
        String cleaned = sanitize(name);
        if (parentId != null && !repository.folderExists(parentId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "parent folder not found");
        Instant now = Instant.now(clock);
        KnowledgeFolder folder = new KnowledgeFolder(UUID.randomUUID(), parentId, cleaned, now, now);
        repository.insertFolder(folder);
        return folder;
    }

    public List<KnowledgeFile> files(UUID folderId) {
        if (folderId != null && !repository.folderExists(folderId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found");
        return repository.listFiles(folderId);
    }

    public List<KnowledgeFile> store(UUID folderId, List<MultipartFile> files) throws IOException {
        if (folderId != null && !repository.folderExists(folderId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found");
        List<KnowledgeFile> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) saved.add(storeOne(folderId, file));
        }
        return saved;
    }

    private KnowledgeFile storeOne(UUID folderId, MultipartFile file) throws IOException {
        String name = sanitize(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String ext = extOf(name);
        UUID id = UUID.randomUUID();
        Path target = storageDir.resolve(id + (ext.isEmpty() ? "" : "." + ext));
        MessageDigest digest = newSha256();
        long size = 0;
        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                size += read;
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        Instant now = Instant.now(clock);
        KnowledgeFile entry = new KnowledgeFile(id, folderId, name, ext,
                file.getContentType(), size, HexFormat.of().formatHex(digest.digest()),
                target.getFileName().toString(), now, now);
        repository.insertFile(entry);
        return entry;
    }

    public KnowledgeFile rename(UUID id, String name) {
        KnowledgeFile existing = requireFile(id);
        String cleaned = sanitize(name);
        String ext = extOf(cleaned);
        repository.renameFile(id, cleaned, ext, Instant.now(clock));
        return requireFile(id);
    }

    public KnowledgeFile move(UUID id, UUID folderId) {
        requireFile(id);
        if (folderId != null && !repository.folderExists(folderId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target folder not found");
        repository.moveFile(id, folderId, Instant.now(clock));
        return requireFile(id);
    }

    public void delete(UUID id) {
        KnowledgeFile existing = requireFile(id);
        repository.deleteFile(id);
        try {
            Files.deleteIfExists(storageDir.resolve(existing.storagePath()));
        } catch (IOException ignored) {
        }
    }

    public KnowledgeFolder requireFolder(UUID id) {
        return repository.listFolders().stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found"));
    }

    public void deleteFolder(UUID id) {
        for (UUID fileId : repository.listFileIdsByFolder(id)) {
            delete(fileId);
        }
        for (UUID subId : repository.listSubfolderIds(id)) {
            deleteFolder(subId);
        }
        repository.deleteFolder(id);
    }

    public KnowledgeFile requireFile(UUID id) {
        return repository.findFile(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found"));
    }

    public SearchResult search(String keyword) {
        String like = keyword.trim();
        if (like.isEmpty()) return new SearchResult(List.of(), List.of());
        List<KnowledgeFolder> matchedFolders = repository.searchFolders(like);
        List<KnowledgeFile> matchedFiles = repository.searchFiles(like);

        List<KnowledgeFolder> allFolders = repository.listFolders();
        java.util.Map<UUID, KnowledgeFolder> folderMap = new java.util.HashMap<>();
        for (KnowledgeFolder f : allFolders) folderMap.put(f.id(), f);

        List<SearchedFolder> folders = new ArrayList<>();
        for (KnowledgeFolder f : matchedFolders)
            folders.add(new SearchedFolder(f.id(), f.parentId(), f.name(), f.createdAt(), f.updatedAt(), resolvePath(f.id(), folderMap)));

        List<SearchedFile> files = new ArrayList<>();
        for (KnowledgeFile f : matchedFiles)
            files.add(new SearchedFile(f.id(), f.folderId(), f.name(), f.ext(), f.mimeType(), f.sizeBytes(), f.sha256(), f.storagePath(), f.createdAt(), f.updatedAt(), resolvePath(f.folderId(), folderMap)));

        return new SearchResult(folders, files);
    }

    private String resolvePath(UUID folderId, java.util.Map<UUID, KnowledgeFolder> folderMap) {
        java.util.ArrayDeque<String> segments = new java.util.ArrayDeque<>();
        UUID current = folderId;
        while (current != null) {
            KnowledgeFolder f = folderMap.get(current);
            if (f == null) break;
            segments.addFirst(f.name());
            current = f.parentId();
        }
        return segments.isEmpty() ? "/" : "/ " + String.join(" / ", segments);
    }

    public record SearchResult(List<SearchedFolder> folders, List<SearchedFile> files) {}
    public record SearchedFolder(UUID id, UUID parentId, String name, Instant createdAt, Instant updatedAt, String path) {}
    public record SearchedFile(UUID id, UUID folderId, String name, String ext, String mimeType, long sizeBytes, String sha256, String storagePath, Instant createdAt, Instant updatedAt, String path) {}

    public PreviewResult preview(UUID id) throws IOException {
        KnowledgeFile file = requireFile(id);
        Path path = resolve(file);
        String mime = file.mimeType() != null && !file.mimeType().isBlank() ? file.mimeType() : "application/octet-stream";
        long size = Files.size(path);
        String ext = file.ext() != null ? file.ext().toLowerCase() : "";

        java.util.Set<String> textExts = java.util.Set.of("txt","md","log","csv","json","xml","yml","yaml",
                "properties","cfg","conf","ini","sh","bat","cmd","ps1",
                "py","js","ts","jsx","tsx","java","kt","scala","go","rs",
                "c","cpp","h","hpp","cs","php","rb","pl","lua","r",
                "sql","html","htm","css","scss","less","sass","vue",
                "gradle","toml","dockerfile","env","gitignore","tex","bib","rst","adoc","asciidoc");
        java.util.Set<String> imageExts = java.util.Set.of("png","jpg","jpeg","gif","webp","svg","bmp","ico","avif","tiff","tif","heic","heif");
        java.util.Set<String> officeExts = java.util.Set.of("doc","docx","xls","xlsx","ppt","pptx","odt","ods","odp","rtf");
        java.util.Set<String> pdfExts = java.util.Set.of("pdf");

        String kind, content = null, contentBase64 = null;

        if (imageExts.contains(ext) && !"svg".equals(ext)) {
            kind = "image";
            byte[] bytes = Files.readAllBytes(path);
            contentBase64 = java.util.Base64.getEncoder().encodeToString(bytes);
        } else if ("svg".equals(ext)) {
            kind = "text";
            content = Files.readString(path, StandardCharsets.UTF_8);
            mime = "image/svg+xml";
        } else if (pdfExts.contains(ext)) {
            kind = "pdf";
        } else if (textExts.contains(ext) || mime.startsWith("text/")) {
            kind = "text";
            try {
                content = Files.readString(path, StandardCharsets.UTF_8);
            } catch (java.nio.charset.MalformedInputException e) {
                byte[] raw = Files.readAllBytes(path);
                java.nio.charset.Charset[] fallbacks = {
                    java.nio.charset.Charset.forName("GBK"),
                    java.nio.charset.Charset.forName("GB18030"),
                };
                content = null;
                for (java.nio.charset.Charset cs : fallbacks) {
                    try {
                        java.nio.charset.CharsetDecoder decoder = cs.newDecoder();
                        decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                        decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                        content = decoder.decode(java.nio.ByteBuffer.wrap(raw)).toString();
                        break;
                    } catch (Exception ignored) {}
                }
                if (content == null) content = new String(raw, StandardCharsets.ISO_8859_1);
            }
            if (content.length() > 512 * 1024)
                content = content.substring(0, 512 * 1024) + "\n\n... (file too large, preview truncated to 512KB)";
        } else if (officeExts.contains(ext)) {
            kind = "text";
            try {
                Tika tika = new Tika();
                content = tika.parseToString(path);
                if (content.length() > 1024 * 1024)
                    content = content.substring(0, 1024 * 1024) + "\n\n... (file too large, preview truncated to 1MB)";
            } catch (TikaException e) {
                throw new IOException("Failed to parse Office document", e);
            }
        } else {
            kind = "binary";
        }
        return new PreviewResult(file.id(), file.name(), mime, size, ext, kind, content, contentBase64);
    }

    public record PreviewResult(UUID id, String name, String mimeType, long sizeBytes, String ext, String kind, String content, String contentBase64) {}

    public Path resolve(KnowledgeFile file) {
        return storageDir.resolve(file.storagePath()).normalize();
    }

    private static String renderOfficeToHtml(java.nio.file.Path path, String ext, String fileName) throws Exception {
        StringBuilder html = new StringBuilder(8192);
        html.append("<div class='kb-office-render'>");
        String lower = ext.toLowerCase();
        try {
            if (lower.equals("docx")) {
                try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
                    org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is);
                    html.append("<h3 style='margin:0 0 12px;font-size:15px;font-weight:600;color:#171719'>").append(escapeHtml(fileName)).append("</h3>");
                    html.append("<div style='font-size:13px;line-height:1.7;color:#171719'>");
                    int pCount = 0;
                    for (var para : doc.getParagraphs()) {
                        String text = para.getText().trim();
                        if (!text.isEmpty()) {
                            if (pCount > 0) html.append("<br>");
                            html.append(escapeHtml(text));
                            pCount++;
                            if (pCount > 200) { html.append("<br><em style='color:#999'>... (document truncated)</em>"); break; }
                        }
                    }
                    html.append("</div>");
                }
            } else if (lower.equals("xlsx")) {
                try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
                    org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(is);
                    html.append("<h3 style='margin:0 0 12px;font-size:15px;font-weight:600;color:#171719'>").append(escapeHtml(fileName)).append("</h3>");
                    html.append("<div style='overflow-x:auto'><table style='border-collapse:collapse;font-size:12px;width:100%'>");
                    for (int i = 0; i < Math.min(wb.getNumberOfSheets(), 3); i++) {
                        var sheet = wb.getSheetAt(i);
                        if (wb.getNumberOfSheets() > 1)
                            html.append("<caption style='text-align:left;font-weight:600;padding:8px 0 4px'>").append(escapeHtml(sheet.getSheetName())).append("</caption>");
                        int maxRows = Math.min(sheet.getPhysicalNumberOfRows(), 80);
                        for (int r = 0; r < maxRows; r++) {
                            var row = sheet.getRow(r);
                            html.append("<tr>");
                            int maxCols = Math.min(row == null ? 0 : row.getPhysicalNumberOfCells(), 20);
                            for (int c = 0; c < Math.max(maxCols, 1); c++) {
                                var cell = row == null ? null : row.getCell(c);
                                String val = cell == null ? "" : new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell);
                                boolean isHeader = r == 0;
                                html.append("<td style='border:1px solid #e0e0e0;padding:4px 8px;").append(isHeader ? "font-weight:600;background:#f5f5f5" : "").append("'>").append(escapeHtml(val)).append("</td>");
                            }
                            html.append("</tr>");
                        }
                    }
                    html.append("</table></div>");
                }
            } else if (lower.equals("pptx")) {
                try (java.io.InputStream is = java.nio.file.Files.newInputStream(path)) {
                    org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(is);
                    html.append("<h3 style='margin:0 0 12px;font-size:15px;font-weight:600;color:#171719'>").append(escapeHtml(fileName)).append("</h3>");
                    int slideCount = 0;
                    for (var slide : ppt.getSlides()) {
                        slideCount++;
                        if (slideCount > 20) { html.append("<br><em style='color:#999'>... (slides truncated)</em>"); break; }
                        html.append("<div style='background:#f8f9fa;border:1px solid #e0e0e0;border-radius:4px;padding:10px 14px;margin-bottom:8px'>");
                        html.append("<div style='font-size:11px;color:#999;margin-bottom:4px'>Slide ").append(slideCount).append("</div>");
                        for (var shape : slide.getShapes()) {
                            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape) {
                                String t = ((org.apache.poi.xslf.usermodel.XSLFTextShape) shape).getText().trim();
                                if (!t.isEmpty()) html.append("<div style='font-size:13px;line-height:1.5'>").append(escapeHtml(t)).append("</div>");
                            }
                        }
                        html.append("</div>");
                    }
                }
            } else {
                Tika tika = new Tika();
                String text = tika.parseToString(path);
                html.append("<pre style='font-size:13px;line-height:1.6;white-space:pre-wrap;font-family:monospace'>").append(escapeHtml(text)).append("</pre>");
            }
        } catch (Exception e) {
            html.append("<p style='color:#c9352b'>Preview failed: ").append(escapeHtml(e.getMessage())).append("</p>");
            throw e;
        }
        html.append("</div>");
        return html.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String sanitize(String raw) {
        String cleaned = raw.replace('\\', '/');
        cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1);
        cleaned = cleaned.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) cleaned = "unnamed";
        if (cleaned.length() > MAX_NAME_LENGTH) cleaned = cleaned.substring(cleaned.length() - MAX_NAME_LENGTH);
        return cleaned;
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return ext.length() > 16 ? "" : ext;
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
