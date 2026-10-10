package com.nova.approval.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 결재 이력 해시 체인의 한 행. 전역 단일 체인이며 INSERT만 한다.
 * hash = SHA-256(prev_hash ‖ seq ‖ document_id ‖ actor_id ‖ action ‖ acted_at)
 */
@Entity
@Table(name = "approval_history")
public class ApprovalHistory {

    // seq는 해시 계산에 필요하므로 삽입 전에 직접 부여한다 (ApprovalHistoryService에서 직렬화).
    @Id
    private Long seq;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64, unique = true)
    private String hash;

    protected ApprovalHistory() {
    }

    public ApprovalHistory(Long documentId, Long actorId, ApprovalAction action, Instant actedAt, String prevHash) {
        this.documentId = documentId;
        this.actorId = actorId;
        this.action = action;
        this.actedAt = actedAt;
        this.prevHash = prevHash;
    }

    public Long getSeq() {
        return seq;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getActorId() {
        return actorId;
    }

    public ApprovalAction getAction() {
        return action;
    }

    public Instant getActedAt() {
        return actedAt;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
