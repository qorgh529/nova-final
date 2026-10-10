package com.nova.approval.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "approval_lines")
public class ApprovalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int step;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineStatus status;

    @Column(name = "acted_at")
    private Instant actedAt;

    protected ApprovalLine() {
    }

    public ApprovalLine(Long documentId, int step, Long approverId) {
        this.documentId = documentId;
        this.step = step;
        this.approverId = approverId;
        this.status = LineStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public int getStep() {
        return step;
    }

    public Long getApproverId() {
        return approverId;
    }

    public LineStatus getStatus() {
        return status;
    }

    public void act(LineStatus status) {
        this.status = status;
        this.actedAt = Instant.now();
    }

    public Instant getActedAt() {
        return actedAt;
    }
}
