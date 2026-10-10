package com.nova.approval.attachment;

import com.nova.approval.domain.Attachment;
import com.nova.approval.domain.Document;
import com.nova.approval.repo.ApprovalLineRepository;
import com.nova.approval.repo.AttachmentRepository;
import com.nova.approval.repo.DocumentRepository;
import com.nova.approval.storage.StorageService;
import com.nova.approval.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final DocumentRepository documents;
    private final ApprovalLineRepository lines;
    private final StorageService storage;

    public AttachmentService(AttachmentRepository attachments, DocumentRepository documents,
                             ApprovalLineRepository lines, StorageService storage) {
        this.attachments = attachments;
        this.documents = documents;
        this.lines = lines;
        this.storage = storage;
    }

    @Transactional
    public Attachment upload(Long documentId, Long actorId, String fileName, String contentType, byte[] content) {
        Document doc = documents.findById(documentId)
            .orElseThrow(() -> ApiException.notFound("문서 없음: " + documentId));
        if (!doc.getDrafterId().equals(actorId)) {
            throw ApiException.forbidden("기안자만 첨부할 수 있습니다");
        }
        String sha256 = sha256Hex(content);
        String objectKey = "documents/" + documentId + "/" + UUID.randomUUID() + "-" + sanitize(fileName);
        storage.put(objectKey, content, contentType);
        return attachments.save(new Attachment(documentId, objectKey, fileName, contentType, content.length, sha256));
    }

    @Transactional(readOnly = true)
    public String presignedDownloadUrl(Long attachmentId, Long actorId) {
        Attachment att = attachments.findById(attachmentId)
            .orElseThrow(() -> ApiException.notFound("첨부 없음: " + attachmentId));
        requireRelated(att.getDocumentId(), actorId);
        return storage.presignedGetUrl(att.getObjectKey(), Duration.ofMinutes(5));
    }

    @Transactional(readOnly = true)
    public List<Attachment> listForDocument(Long documentId) {
        return attachments.findByDocumentId(documentId);
    }

    private void requireRelated(Long documentId, Long actorId) {
        Document doc = documents.findById(documentId)
            .orElseThrow(() -> ApiException.notFound("문서 없음: " + documentId));
        boolean related = doc.getDrafterId().equals(actorId)
            || lines.findByDocumentIdOrderByStep(documentId).stream()
                .anyMatch(l -> l.getApproverId().equals(actorId));
        if (!related) {
            throw ApiException.forbidden("문서 관련자가 아닙니다");
        }
    }

    private static String sanitize(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
