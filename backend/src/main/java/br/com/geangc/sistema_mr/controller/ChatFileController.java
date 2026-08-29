package br.com.geangc.sistema_mr.controller;

import br.com.geangc.sistema_mr.controller.dto.ChatFileDto;
import br.com.geangc.sistema_mr.service.DocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/chat/files")
public class ChatFileController {

    private final DocumentService documentService;

    public ChatFileController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ChatFileDto>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documentService.upload(
                files, AiController.conversationIdFor(jwt.getSubject()), jwt.getSubject()
        ).stream().map(ChatFileDto::from).toList());
    }

    @GetMapping
    public List<ChatFileDto> list(@AuthenticationPrincipal Jwt jwt) {
        return documentService.list(AiController.conversationIdFor(jwt.getSubject()), jwt.getSubject())
                .stream().map(ChatFileDto::from).toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        var download = documentService.download(id, AiController.conversationIdFor(jwt.getSubject()), jwt.getSubject());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.file().mimeType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.file().size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.file().originalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(download.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.delete(id, AiController.conversationIdFor(jwt.getSubject()), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
