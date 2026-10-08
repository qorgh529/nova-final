package com.nova.approval.web;

import com.nova.approval.attachment.AttachmentService;
import com.nova.approval.auth.CurrentUser;
import com.nova.approval.domain.Attachment;
import com.nova.approval.web.dto.Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping("/documents/{id}/attachments")
    public ResponseEntity<Dtos.AttachmentView> upload(@PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("빈 파일입니다");
        }
        Attachment att = attachments.upload(id, CurrentUser.id(),
            file.getOriginalFilename(), file.getContentType(), file.getBytes());
        Dtos.AttachmentView view = new Dtos.AttachmentView(att.getId(), att.getFileName(),
            att.getContentType(), att.getSize(), att.getSha256(), att.getUploadedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<Void> download(@PathVariable Long id) {
        String url = attachments.presignedDownloadUrl(id, CurrentUser.id());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
