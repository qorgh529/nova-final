package com.nova.approval.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_at", nullable = false, updatable = false, insertable = false)
    private Instant uploadedAt;

    protected Attachment() {
    }

    public Attachment(Long documentId, String objectKey, String fileName, String contentType, long size, String sha256) {
        this.documentId = documentId;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.sha256 = sha256;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
