package com.subtlesight.server;

import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFile;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFolder;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocumentVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.subtlesight.server.KnowledgeService.SearchResult;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/folders")
    List<KnowledgeFolder> folders() {
        return knowledge.folders();
    }

    @PostMapping("/folders")
    KnowledgeFolder createFolder(@Valid @RequestBody FolderRequest request) {
        return knowledge.createFolder(request.parentId(), request.name());
    }

    @GetMapping("/files")
    List<KnowledgeFile> files(@RequestParam(required = false) UUID folderId) {
        return knowledge.files(folderId);
    }

    @GetMapping("/documents")
    List<KnowledgeDocument> documents(@RequestParam(required = false) UUID folderId,
                                      @RequestParam(defaultValue = "true") boolean all) {
        return knowledge.documents(folderId, all);
    }

    @PostMapping("/documents")
    KnowledgeDocument createDocument(@RequestBody DocumentCreateRequest request) {
        return knowledge.createDocument(
                request.folderId(), request.title(), request.contentHtml(), request.drawingJson());
    }

    @GetMapping("/documents/{id}")
    KnowledgeDocument document(@PathVariable UUID id) {
        return knowledge.requireDocument(id);
    }

    @PutMapping("/documents/{id}")
    KnowledgeDocument updateDocument(@PathVariable UUID id, @RequestBody DocumentUpdateRequest request) {
        return knowledge.updateDocument(
                id, request.folderId(), request.title(), request.contentHtml(), request.drawingJson(),
                request.expectedVersion(), request.changeSummary());
    }

    @DeleteMapping("/documents/{id}")
    void deleteDocument(@PathVariable UUID id) {
        knowledge.deleteDocument(id);
    }

    @GetMapping("/documents/{id}/versions")
    List<KnowledgeDocumentVersion> documentVersions(@PathVariable UUID id) {
        return knowledge.documentVersions(id);
    }

    @GetMapping("/documents/{id}/versions/{version}")
    KnowledgeDocumentVersion documentVersion(@PathVariable UUID id, @PathVariable int version) {
        return knowledge.requireDocumentVersion(id, version);
    }

    @PostMapping("/documents/{id}/versions/{version}/restore")
    KnowledgeDocument restoreDocumentVersion(@PathVariable UUID id, @PathVariable int version,
                                             @RequestBody RestoreVersionRequest request) {
        return knowledge.restoreDocumentVersion(id, version, request.expectedVersion());
    }

    @PostMapping("/documents/{id}/ai-assist")
    KnowledgeService.AiSuggestion assistDocument(@PathVariable UUID id,
                                                 @RequestBody AiAssistRequest request) {
        return knowledge.assistDocument(id, request.instruction(), request.selectedText());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    List<KnowledgeFile> upload(@RequestParam(required = false) UUID folderId,
                               @RequestPart("files") List<MultipartFile> files) throws IOException {
        return knowledge.store(folderId, files);
    }

    @PostMapping("/files/{id}/rename")
    KnowledgeFile rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return knowledge.rename(id, request.name());
    }

    @PostMapping("/files/{id}/move")
    KnowledgeFile move(@PathVariable UUID id, @RequestBody MoveRequest request) {
        return knowledge.move(id, request.folderId());
    }

    @DeleteMapping("/files/{id}")
    void delete(@PathVariable UUID id) {
        knowledge.delete(id);
    }

    @DeleteMapping("/folders/{id}")
    void deleteFolder(@PathVariable UUID id) {
        knowledge.deleteFolder(id);
    }

    @GetMapping("/search")
    SearchResult search(@RequestParam("q") String q) {
        return knowledge.search(q);
    }

    /** 流式下载，避免大文件全量载入内存 */
    @GetMapping("/files/{id}/download")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws IOException {
        KnowledgeFile file = knowledge.requireFile(id);
        Path path = knowledge.resolve(file);
        String mime = file.mimeType() == null || file.mimeType().isBlank() ? "application/octet-stream" : file.mimeType();
        String encoded = java.net.URLEncoder.encode(file.name(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(Files.size(path))
                .contentType(MediaType.parseMediaType(mime))
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    /** 流式查看（inline），用于 iframe 内联预览 PDF/Office */
    @GetMapping("/files/{id}/view")
    ResponseEntity<InputStreamResource> view(@PathVariable UUID id) throws IOException {
        KnowledgeFile file = knowledge.requireFile(id);
        Path path = knowledge.resolve(file);
        String mime = resolveMimeType(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(file.name(), StandardCharsets.UTF_8).replace("+", "%20"))
                .header("X-Frame-Options", "SAMEORIGIN")
                .contentLength(Files.size(path))
                .contentType(MediaType.parseMediaType(mime))
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    private static String resolveMimeType(KnowledgeFile file) {
        String mime = file.mimeType();
        if (mime != null && !mime.isBlank() && !mime.equals("application/octet-stream")) return mime;
        String ext = file.ext() != null ? file.ext().toLowerCase() : "";
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "odp" -> "application/vnd.oasis.opendocument.presentation";
            case "rtf" -> "application/rtf";
            case "csv" -> "text/csv";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    @GetMapping("/files/{id}/preview")
    KnowledgeService.PreviewResult preview(@PathVariable UUID id) throws IOException {
        return knowledge.preview(id);
    }

    public record FolderRequest(UUID parentId, @NotBlank String name) {}
    public record RenameRequest(@NotBlank String name) {}
    public record MoveRequest(UUID folderId) {}
    public record DocumentCreateRequest(UUID folderId, String title, String contentHtml, String drawingJson) {}
    public record DocumentUpdateRequest(UUID folderId, String title, String contentHtml, String drawingJson,
                                        int expectedVersion, String changeSummary) {}
    public record RestoreVersionRequest(int expectedVersion) {}
    public record AiAssistRequest(String instruction, String selectedText) {}
}
